package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache

import assertk.assertThat
import assertk.assertions.*
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractPostgresTest
import no.nav.k9.los.buildAndTestConfig
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.felter
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Action
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger.Jobbplanlegger
import no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger.PlanlagtJobb
import no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger.Tidsvindu
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgavefilterRens
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
            coEvery { pepClient.harTilgangTilOppgaveV3(any(), any(), any()) } returns true
        }

        val områdeSetup = get<OmrådeSetup>()
        områdeSetup.setup()
    }

    fun gjørSakKode6(saksnummer: String) {
        runBlocking {
            coEvery { pepClient.erSakKode6(eq(saksnummer)) } returns true
            coEvery { pepClient.erSakKode7EllerEgenAnsatt(eq(saksnummer)) } returns false
            coEvery { pepClient.diskresjonskoderForSak(eq(saksnummer)) } returns setOf(Diskresjonskode.KODE6)
        }
    }

    fun gjørSakOrdinær(saksnummer: String) {
        runBlocking {
            coEvery { pepClient.erSakKode6(eq(saksnummer)) } returns false
            coEvery { pepClient.erSakKode7EllerEgenAnsatt(eq(saksnummer)) } returns false
            coEvery { pepClient.diskresjonskoderForSak(eq(saksnummer)) } returns setOf()
        }
    }

    fun gjørAktørKode6(aktørId: String) {
        runBlocking {
            coEvery { pepClient.erAktørKode6(eq(aktørId)) } returns true
            coEvery { pepClient.erAktørKode7EllerEgenAnsatt(eq(aktørId)) } returns false
            coEvery { pepClient.diskresjonskoderForPerson(eq(aktørId)) } returns setOf(Diskresjonskode.KODE6)
        }
    }

    fun gjørAktørKode7(aktørId: String) {
        runBlocking {
            coEvery { pepClient.erAktørKode6(eq(aktørId)) } returns false
            coEvery { pepClient.erAktørKode7EllerEgenAnsatt(eq(aktørId)) } returns true
            coEvery { pepClient.diskresjonskoderForPerson(eq(aktørId)) } returns setOf(Diskresjonskode.KODE7)
        }
    }

    fun gjørAktørOrdinær(aktørId: String) {
        runBlocking {
            coEvery { pepClient.erAktørKode6(eq(aktørId)) } returns false
            coEvery { pepClient.erAktørKode7EllerEgenAnsatt(eq(aktørId)) } returns false
            coEvery { pepClient.diskresjonskoderForPerson(eq(aktørId)) } returns setOf()
        }
    }


    @Test
    fun `Alle ordinære eventer på K9sakEventHandler skal oppdatere pepcache for å alltid få med aktørendringer i sak`() {
        val k9sakEventHandler = get<K9SakEventHandler>()
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
    fun `Alle ordinære eventer på K9punsjEventHandler skal oppdatere pepcache for å alltid få med aktørendringer i sak`() {
        val k9punsjEventHandler = get<no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler>()
        val pepRepository = get<PepCacheRepository>()

        val aktørId = "1234567890123"
        val eksternId = UUID.randomUUID().toString()
        gjørAktørOrdinær(aktørId)
        k9punsjEventHandler.prosesser(lagPunsjBehandlingprosessEventMedStatus(eksternId, aktørId))

        val pepCache = pepRepository.hent("K9", eksternId)!!
        assertThat(pepCache.kode6).isFalse()
        assertThat(pepCache.kode7).isFalse()
        assertThat(pepCache.egenAnsatt).isFalse()
        assertThat(pepCache.oppdatert).isGreaterThan(LocalDateTime.now().minusMinutes(1))
    }

    @Test
    fun `Eventer i K9punsjEventHandler med kode6 skal oppdatere pepcache`() {
        val k9punsjEventHandler = get<no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler>()
        val pepRepository = get<PepCacheRepository>()

        val aktørId = "1234567890123"
        val eksternId = UUID.randomUUID().toString()
        gjørAktørKode6(aktørId)
        k9punsjEventHandler.prosesser(lagPunsjBehandlingprosessEventMedStatus(eksternId, aktørId))

        val pepCache = pepRepository.hent("K9", eksternId)!!
        assertThat(pepCache.kode6).isTrue()
        assertThat(pepCache.kode7).isFalse()
        assertThat(pepCache.egenAnsatt).isFalse()
        assertThat(pepCache.oppdatert).isGreaterThan(LocalDateTime.now().minusMinutes(1))
    }

    @Test
    fun `Eventer i K9punsjEventHandler med kode7 skal oppdatere pepcache`() {
        val k9punsjEventHandler = get<no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler>()
        val pepRepository = get<PepCacheRepository>()

        val aktørId = "1234567890123"
        val eksternId = UUID.randomUUID().toString()
        gjørAktørKode7(aktørId)
        k9punsjEventHandler.prosesser(lagPunsjBehandlingprosessEventMedStatus(eksternId, aktørId))

        val pepCache = pepRepository.hent("K9", eksternId)!!
        assertThat(pepCache.kode6).isFalse()
        assertThat(pepCache.kode7).isTrue()
        assertThat(pepCache.oppdatert).isGreaterThan(LocalDateTime.now().minusMinutes(1))
    }

    @Test
    fun `Eventer i K9sakEventHandler med kode6 skal oppdatere pepcache`() {
        val k9sakEventHandler = get<K9SakEventHandler>()
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
        val k9sakEventHandler = get<K9SakEventHandler>()
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
    fun `PepCacheService skal oppdatere oppgave når sikkerhetsklassifisering endrer seg`() = runTest {
        val k9sakEventHandler = get<K9SakEventHandler>()
        val pepRepository = get<PepCacheRepository>()
        val oppgaveQueryService = get<OppgaveQueryService>()

        val pepCacheService = PepCacheService(
            pepCacheRepository = pepRepository,
            pepClient = pepClient,
            oppgaveRepository = get(),
            transactionalManager = get()
        )

        val jobbplanlegger =
            Jobbplanlegger(setOf(PlanlagtJobb.Periodisk("pepcache", 0, Tidsvindu.ÅPENT, 500.milliseconds, 0.seconds) {
                pepCacheService.oppdaterCacheForÅpneOgVentendeOppgaverEldreEnn(gyldighet = Duration.ofNanos(1))
            }), coroutineContext = this.coroutineContext)
        //hvis testen henger, bytt til testScheduler her for å diagnostisere, men vit at testScheduler mest sannsynlig ødelegger testen
        // , så du må mest sannsynlig bytte tilbake til coroutineContext for at timing med jobb opp mot cache-endringer og venting på riktig tilstand skal virke
        jobbplanlegger.start()

        val saksnummer = "TEST4"
        gjørSakOrdinær(saksnummer)

        val eksternId = UUID.randomUUID().toString()
        k9sakEventHandler.prosesser(lagBehandlingprosessEventMedStatus(eksternId, saksnummer))

        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, PersonBeskyttelseType.UGRADERT)).isNotEmpty()
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, PersonBeskyttelseType.UTEN_KODE6)).isNotEmpty()
        verify(exactly = 2) { runBlocking { pepClient.harTilgangTilOppgaveV3(any(), Action.read, any()) } } //oppgaven hentet ut 2 ganger fra kø, gir to kall til pep klient
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, PersonBeskyttelseType.KODE6)).isEmpty()
        verify(exactly = 2) { runBlocking { pepClient.harTilgangTilOppgaveV3(any(), Action.read, any()) } } //oppgaven var ikke i køa, så gjør ikke ekstra kall til pep-klent

        gjørSakKode6(saksnummer)
        ventPåAntallForsøk(10, "Pepcache") { pepRepository.hent("K9", eksternId)?.kode6 == true }

        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, PersonBeskyttelseType.UGRADERT)).isEmpty()
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, PersonBeskyttelseType.KODE7_ELLER_EGEN_ANSATT)).isEmpty()
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, PersonBeskyttelseType.UTEN_KODE6)).isEmpty()
        assertThat(hentOppgaverMedSikkerhetsklassifisering(oppgaveQueryService, eksternId, PersonBeskyttelseType.KODE6)).isNotEmpty()

        jobbplanlegger.stopp()
        verify(exactly = 3) { runBlocking { pepClient.harTilgangTilOppgaveV3(any(), any(), any()) } } //oppgaven var bare i kode6-køa, så ble ett ekstra kall til pep-klent
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
        sikkerhetsklassifiseringtype: PersonBeskyttelseType
    ): List<Any> {
        val transactionalManager = get<TransactionalManager>()
        return transactionalManager.transaction { tx ->
            val filtre = listOf(
                CombineOppgavefilter(combineOperator = CombineOperator.AND, filtere = listOf(
                    FeltverdiOppgavefilter(
                        område = null,
                        kode = FeltType.PERSONBESKYTTELSE.eksternId,
                        operator = EksternFeltverdiOperator.EQUALS,
                        verdi = listOf(sikkerhetsklassifiseringtype.kode)
                    ),
                    FeltverdiOppgavefilter(område = "K9",
                        kode = FeltType.BEHANDLINGUUID.eksternId,
                        operator = EksternFeltverdiOperator.EQUALS,
                        verdi = listOf(eksternId)
                    )
                ))
            )

            oppgaveQueryService.query(tx,
                QueryRequest(OppgaveQuery(
                    select = listOf(EnkelSelectFelt(område = "K9", kode = "ekstern_id")),
                    filtere = OppgavefilterRens.rens(felter, filtre)
                )),
                mockk(relaxed = true),
            )
        }
    }

    private fun lagBehandlingprosessEventMedStatus(
        eksternId: String,
        saksnummer: String,
        behandlingStatus: BehandlingStatus = BehandlingStatus.UTREDES,
        eventTid: LocalDateTime = LocalDateTime.now(),
        eventHendelse: EventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT
    ): K9SakEventDto {

        val objectMapper = jacksonObjectMapper()
            .dusseldorfConfigured().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)

        @Language("JSON") val json =
            """{
                "merknader": [],
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
                  },
                  "aksjonspunktTilstander": [
                    {
                        "status": {
                        "kode": "OPPR",
                        "kodeverk": "AKSJONSPUNKT_STATUS"
                    },
                    "fristTid": null,
                    "venteårsak": {
                    "kode": "-",
                    "kodeverk": "VENT_AARSAK",
                    "kanVelgesIGui": false
                    },
                    "aksjonspunktKode": "5009"
                    }
                    ]
            }"""

        return objectMapper.readValue(json, K9SakEventDto::class.java)
    }

    private fun lagPunsjBehandlingprosessEventMedStatus(
        eksternId: String,
        aktørId: String,
        eventTid: LocalDateTime = LocalDateTime.now(),
    ): PunsjEventDto {

        return PunsjEventDto(
            eksternId = UUID.fromString(eksternId),
            journalpostId = JournalpostId("1"),
            eventTid = eventTid,
            aktørId = AktørId(aktørId),
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
        )
    }

    private suspend fun ventPåAntallForsøk(antall: Int, beskrivelse: String = "", f: () -> Boolean) {
        for (i in 0..antall) {
            delay(500)
            if (i == antall) throw IllegalStateException("Ikke oppfylt innen tidsfrist: $beskrivelse")
            if (f()) {
                logger.info("Oppfylt innen tidsfrist: $beskrivelse")
                break
            }
        }
    }
}