package no.nav.k9.los.nyoppgavestyring.pep

import assertk.assertThat
import assertk.assertions.*
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractPostgresTest
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.buildAndTestConfig
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.nyoppgavestyring.kodeverk.SikkerhetsklassifiseringType
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefilterUtvider
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class PepCacheServiceTest : KoinTest, AbstractPostgresTest() {

    val saksnummerOrdinær = "1111"
    val saksnummerKode6 = "6666"

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
            gjørSakOrdinær(saksnummerOrdinær)
            gjørSakKode6(saksnummerKode6)
        }
    }

    fun gjørSakKode6(saksnummer: String) {
        runBlocking {
            coEvery { pepClient.erSakKode6(eq(saksnummer)) } returns true
            coEvery { pepClient.erSakKode7EllerEgenAnsatt(eq(saksnummer)) } returns true
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

        val eksternId = UUID.randomUUID().toString()
        k9sakEventHandler.prosesser(lagBehandlingprosessEventMedStatus(eksternId, saksnummerOrdinær))

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

        val eksternId = UUID.randomUUID().toString()
        k9sakEventHandler.prosesser(lagBehandlingprosessEventMedStatus(eksternId, saksnummerKode6))

        val pepCache = pepRepository.hent("K9", eksternId)!!
        assertThat(pepCache.kode6).isTrue()
        assertThat(pepCache.kode7).isTrue()
        assertThat(pepCache.egenAnsatt).isTrue()
        assertThat(pepCache.oppdatert).isGreaterThan(LocalDateTime.now().minusMinutes(1))
    }

    @Test
    fun `oppdaterCacheForOppgaverEldreEnn skal oppdatere alle oppgaver eldre enn oppgitt alder`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val pepRepository = mockk<PepCacheRepository>(relaxed = true)


        val pepCacheService = PepCacheService(
            pepCacheRepository = pepRepository,
            pepClient = pepClient,
            oppgaveRepository = get(),
            transactionalManager = get()
        )

        val saksnummer = UUID.randomUUID().toString()
        gjørSakOrdinær(saksnummer)

        val eksternId = UUID.randomUUID().toString()
        k9sakEventHandler.prosesser(lagBehandlingprosessEventMedStatus(eksternId, saksnummer))
        Thread.sleep(1000)

        pepCacheService.oppdaterCacheForOppgaverEldreEnn(gyldighet = Duration.ofHours(2))
        verify(exactly = 0) { pepRepository.lagre(any(), any()) }

        loggAlleOppgaverMedFelterOgCache()

        val tidspunktForsøktOppdatert = LocalDateTime.now()
        pepCacheService.oppdaterCacheForOppgaverEldreEnn(gyldighet = Duration.ofNanos(1))

        val slot = slot<PepCache>()
        verify(exactly = 1) { pepRepository.lagre(capture(slot), any()) }
        assertThat(slot.captured.oppdatert).isGreaterThanOrEqualTo(tidspunktForsøktOppdatert)
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

    @Test
    @Disabled
    fun `PepCacheService skal oppdatere oppgave når sikkerhetsklassifisering endrer seg`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val pepCacheService = get<PepCacheService>()
        val oppgaveQueryService = get<OppgaveQueryService>()

        PepCacheOppdaterer(
            pepCacheService,
            tidMellomKjøring = Duration.ofNanos(1),
            alderForOppfriskning = Duration.ofNanos(1)
        ).start()

        val saksnummer = UUID.randomUUID().toString()
        gjørSakOrdinær(saksnummer)

        // Gir tilgang til kode6 for å isolere testing av cache-oppdatering
        runBlocking { coEvery { pepClient.harTilgangTilLesSak(eq(saksnummer), any()) } returns true }

        val eksternId = UUID.randomUUID().toString()
        k9sakEventHandler.prosesser(lagBehandlingprosessEventMedStatus(eksternId, saksnummer))

        loggAlleOppgaverMedFelterOgCache()

        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService)).isNotEmpty()
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, SikkerhetsklassifiseringType.KODE6)).isEmpty()

        gjørSakKode6(saksnummer)
        Thread.sleep(2000)

        loggAlleOppgaverMedFelterOgCache()

        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService)).isEmpty()
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, SikkerhetsklassifiseringType.KODE6)).isNotEmpty()
        verify(exactly = 1) { runBlocking { pepClient.harTilgangTilLesSak(eq(saksnummer), any()) } }
    }


    private fun hentOppgaverMedSikkerhetsklassifisering(
        oppgaveQueryService: OppgaveQueryService,
        vararg sikkerhetsklassifiseringtyper: SikkerhetsklassifiseringType
    ): List<Any> {
        val transactionalManager = get<TransactionalManager>()
        return transactionalManager.transaction { tx ->
            val filtre = FeltverdiOppgavefilter(
                område = null,
                kode = "sikkerhetsklassifisering",
                operator = "IN",
                verdi = sikkerhetsklassifiseringtyper.map { it.kode }
            )

            oppgaveQueryService.query(tx,
                OppgaveQuery(
                    select = listOf(EnkelSelectFelt(område = "K9", kode = "ekstern_id")),
                    filtere = OppgavefilterUtvider.utvid(listOf(filtre))
                ),
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
}