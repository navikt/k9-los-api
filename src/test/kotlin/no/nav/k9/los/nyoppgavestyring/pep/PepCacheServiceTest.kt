package no.nav.k9.los.nyoppgavestyring.pep

import assertk.assertThat
import assertk.assertions.*
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractPostgresTest
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.buildAndTestConfig
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.integrasjon.abac.Action
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.kodeverk.BeskyttelseType
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgavefilterUtvider
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class PepCacheServiceTest : KoinTest, AbstractPostgresTest() {

    val pepClient = mockk<IPepClient>()
    private val logger: Logger = LoggerFactory.getLogger(PepCacheServiceTest::class.java)

    @JvmField
    @RegisterExtension
    val koinTestRule = KoinTestExtension.create {
        modules(buildAndTestConfig(dataSource, pepClient))
    }

    @BeforeEach
    fun setup() {
        runBlocking {
            // Gir tilgang til kode6 for å isolere testing av cache-oppdatering
            coEvery { pepClient.harTilgangTilOppgave(any()) } returns true
            coEvery { pepClient.harTilgangTilOppgaveV3(any(), any(), any()) } returns true
        }

        val områdeSetup = get<OmrådeSetup>()
        områdeSetup.setup()
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        k9SakTilLosAdapterTjeneste.setup()
    }

    fun gjørSakKode6(saksnummer: String) {
        runBlocking {
            coEvery { pepClient.erSakKode6(eq(saksnummer)) } returns true
            coEvery { pepClient.erSakKode7EllerEgenAnsatt(eq(saksnummer)) } returns false
        }
    }

    fun gjørSakOrdinær(saksnummer: String) {
        runBlocking {
            coEvery { pepClient.erSakKode6(eq(saksnummer)) } returns false
            coEvery { pepClient.erSakKode7EllerEgenAnsatt(eq(saksnummer)) } returns false
        }
    }


    @Test
    fun `Alle ordinære eventer på K9sakEventHandler skal oppdatere pepcache for å alltid få med aktørendringer i sak`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val pepRepository = get<PepCacheRepository>()

        val saksnummer = "TEST1"
        val eksternId = UUID.randomUUID().toString()
        gjørSakOrdinær(saksnummer)
        k9sakEventHandler.prosesser(lagBehandlingprosessEventMedStatus(eksternId, saksnummer))

        val pepCache = pepRepository.hent("K9", eksternId)!!
        assertThat(pepCache.kode6).isFalse()
        assertThat(pepCache.kode7).isFalse()
        assertThat(pepCache.egenAnsatt).isFalse()
        assertThat(pepCache.oppdatert).isGreaterThan(LocalDateTime.now().minusMinutes(1))
    }

    @Test
    fun `Eventer i K9sakEventHandler med kode6 skal oppdatere pepcache`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val pepRepository = get<PepCacheRepository>()

        val saksnummer = "TEST2"
        val eksternId = UUID.randomUUID().toString()
        gjørSakKode6(saksnummer)
        k9sakEventHandler.prosesser(lagBehandlingprosessEventMedStatus(eksternId, saksnummer))

        val pepCache = pepRepository.hent("K9", eksternId)!!
        assertThat(pepCache.kode6).isTrue()
        assertThat(pepCache.kode7).isFalse()
        assertThat(pepCache.egenAnsatt).isFalse()
        assertThat(pepCache.oppdatert).isGreaterThan(LocalDateTime.now().minusMinutes(1))
    }

    @Test
    fun `oppdaterCacheForOppgaverEldreEnn skal oppdatere alle oppgaver eldre enn oppgitt alder`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val pepRepository = mockk<PepCacheRepository>(relaxed = true)

        val saksnummer = "TEST3"
        gjørSakOrdinær(saksnummer)

        val pepCacheService = PepCacheService(
            pepCacheRepository = pepRepository,
            pepClient = pepClient,
            oppgaveRepository = get(),
            transactionalManager = get()
        )

        val eksternId = UUID.randomUUID().toString()
        k9sakEventHandler.prosesser(lagBehandlingprosessEventMedStatus(eksternId, saksnummer))

        pepCacheService.oppdaterCacheForÅpneOgVentendeOppgaverEldreEnn(gyldighet = Duration.ofHours(2))
        verify(exactly = 0) { pepRepository.lagre(any(), any()) }

        loggAlleOppgaverMedFelterOgCache()

        val tidspunktForsøktOppdatert = LocalDateTime.now()
        pepCacheService.oppdaterCacheForÅpneOgVentendeOppgaverEldreEnn(gyldighet = Duration.ofNanos(1))

        val slot = slot<PepCache>()
        verify(exactly = 1) { pepRepository.lagre(capture(slot), any()) }
        assertThat(slot.captured.oppdatert).isGreaterThanOrEqualTo(tidspunktForsøktOppdatert)
    }

    @Test
    fun `PepCacheService skal oppdatere oppgave når sikkerhetsklassifisering endrer seg`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val pepRepository = get<PepCacheRepository>()
        val oppgaveQueryService = get<OppgaveQueryService>()

        val pepCacheService = PepCacheService(
            pepCacheRepository = pepRepository,
            pepClient = pepClient,
            oppgaveRepository = get(),
            transactionalManager = get()
        )

        val job = PepCacheOppdaterer(
            pepCacheService,
            tidMellomKjøring = Duration.ofMillis(500),
            alderForOppfriskning = Duration.ofNanos(1),
            forsinketOppstart = Duration.ZERO
        ).startOppdateringAvÅpneOgVentende()

        val saksnummer = "TEST4"
        gjørSakOrdinær(saksnummer)

        val eksternId = UUID.randomUUID().toString()
        k9sakEventHandler.prosesser(lagBehandlingprosessEventMedStatus(eksternId, saksnummer))

        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, BeskyttelseType.ORDINÆR.kode)).isNotEmpty()
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId)).isNotEmpty()
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, "KODE6")).isNotEmpty() // Samme som ordinær frem til håndtert. Kan f.eks gjeninnføres som egen interntype som ikke eksponeres ut

        verify(exactly = 3) { runBlocking { pepClient.harTilgangTilOppgaveV3(any(), Action.read, any()) } }

        gjørSakKode6(saksnummer)
        ventPåAntallForsøk(10, "Pepcache") { pepRepository.hent("K9", eksternId)?.kode6 == true }

        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, BeskyttelseType.ORDINÆR.kode)).isEmpty()
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId)).isNotEmpty()  // Alle beskyttelsetyper er inkludert hvis ikke beskyttelsetype er spesifisert
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, "KODE6")).isEmpty() // Samme som ordinær frem til håndtert


        job.cancel()
        verify(exactly = 4) { runBlocking { pepClient.harTilgangTilOppgaveV3(any(), any(), any()) } }
    }

    private fun loggAlleOppgaverMedFelterOgCache() {
        val oppgaveRepository = get<OppgaveRepository>()
        val testRepository = TestRepository()
        val pepCache = get<PepCacheRepository>()

        val transactionalManager = get<TransactionalManager>()
        transactionalManager.transaction { tx ->
            testRepository.hentEksternIdForAlleOppgaver(tx).forEach { eksternId ->
                logger.info("eksternId $eksternId")
                logger.info("Oppgave: "+oppgaveRepository.hentNyesteOppgaveForEksternId(tx, "K9", eksternId).felter.joinToString(", ") { it.eksternId + "-" + it.verdi })
                logger.info("Pep: "+pepCache.hent("K9", eksternId, tx)?.run { "kode6-$kode6, kode7-$kode7, egenansatt-$egenAnsatt, oppdater-$oppdatert" })
            }
        }
    }

    private fun hentOppgaverMedSikkerhetsklassifisering(
        oppgaveQueryService: OppgaveQueryService,
        eksternId: String,
        vararg sikkerhetsklassifiseringtyper: String
    ): List<Any> {
        val transactionalManager = get<TransactionalManager>()
        return transactionalManager.transaction { tx ->
            val filtre = listOf(
                CombineOppgavefilter(combineOperator = "AND", filtere = listOf(
                    FeltverdiOppgavefilter(
                        område = null,
                        kode = FeltType.BESKYTTELSE.eksternId,
                        operator = "IN",
                        verdi = sikkerhetsklassifiseringtyper.toList()
                    ),
                    FeltverdiOppgavefilter(område = "K9",
                        kode = FeltType.BEHANDLINGUUID.eksternId,
                        operator = "EQUALS",
                        verdi = listOf(eksternId)
                    )
                ))
            )

            oppgaveQueryService.query(tx,
                QueryRequest(OppgaveQuery(
                    select = listOf(EnkelSelectFelt(område = "K9", kode = "ekstern_id")),
                    filtere = OppgavefilterUtvider.utvid(filtre)
                )),
                mockk(relaxed = true),
            )
        }
    }

    private fun lagBehandlingprosessEventMedStatus(
        eksternId: String,
        saksnummer: String,
        behandlingStatus: BehandlingStatus = BehandlingStatus.OPPRETTET,
        eventTid: LocalDateTime = LocalDateTime.now(),
        eventHendelse: EventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT
    ): BehandlingProsessEventDto {

        val objectMapper = jacksonObjectMapper()
            .dusseldorfConfigured().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)

        @Language("JSON") val json =
            """{
                  "eksternId": "$eksternId",
                  "fagsystem": {
                    "kode": "K9SAK",
                    "kodeverk": "FAGSYSTEM"
                  },
                  "saksnummer": "$saksnummer",
                  "aktørId": "9906098522415",
                  "behandlingId": 1000001,
                  "eventTid": "$eventTid",
                  "eventHendelse": "$eventHendelse",
                  "behandlinStatus": "${behandlingStatus.kode}", 
                  "behandlingstidFrist": "2020-03-31",
                  "behandlingStatus": "${behandlingStatus.kode}",
                  "behandlingSteg": "INREG_AVSL",
                  "behandlendeEnhet": "0300",
                  "ytelseTypeKode": "OMP",
                  "behandlingTypeKode": "BT-002",
                  "opprettetBehandling": "2020-02-20T07:38:49",
                  "aksjonspunktKoderMedStatusListe": {
                    "5009": "OPPR"
                  }
            }"""

        return objectMapper.readValue(json, BehandlingProsessEventDto::class.java)
    }

    private fun ventPåAntallForsøk(antall: Int, beskrivelse: String = "", f: () -> Boolean) {
        for (i in 0..antall) {
            Thread.sleep(500)
            if (i == antall) throw IllegalStateException("Ikke oppfylt innen tidsfrist: $beskrivelse")
            if (f()) {
                logger.info("Oppfylt innen tidsfrist: $beskrivelse")
                break
            }
        }
    }
}