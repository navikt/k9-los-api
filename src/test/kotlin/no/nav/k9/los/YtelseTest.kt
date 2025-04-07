package no.nav.k9.los

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.domene.repository.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.get
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Disabled("Kjøres manuelt ved behov")
class YtelseTest : AbstractK9LosIntegrationTest() {

    val random = Random()
    val antallOppgaver = 10000

    @OptIn(DelicateCoroutinesApi::class)
    @BeforeEach
    fun setup() {
        val saksbehandlerRepo = get<SaksbehandlerRepository>()
        runBlocking {
            lagSaksbehandler(saksbehandlerRepo,"Z123456")
            for (testSaksbehandler in TestSaksbehandler.values()) {
                lagSaksbehandler(saksbehandlerRepo, testSaksbehandler.name)
            }
        }

        Kjøretid.logg("Opprettelse og ferdigstillelse av oppgaver") {
            runBlocking {
                coroutineScope {
                    val nå = LocalDateTime.now()
                    (1..antallOppgaver).map {
                        async(Dispatchers.IO) { opprettOgFerdigstillOppgave(nå.minusDays((it / 2).toLong())) }
                    }
                }.awaitAll()
            }
        }
    }

    suspend fun lagSaksbehandler(saksbehandlerRepo: SaksbehandlerRepository, ident : String){
        if (saksbehandlerRepo.finnSaksbehandlerMedIdent(ident) == null) {
            saksbehandlerRepo.addSaksbehandler(
                Saksbehandler(
                    null,
                    ident,
                    ident,
                    ident + "@nav.no",
                    enhet = "1234"
                )
            )
        }
    }

    @Test
    fun `Opprett og ferdigstill oppgave`() {
        val oppgaveTjeneste = get<OppgaveTjeneste>()

        runBlocking {
            Kjøretid.logg("Hent beholdning av oppgaver") {
                val alleOppgaverNyeOgFerdigstilte = oppgaveTjeneste.hentBeholdningAvOppgaverPerAntallDager()
                assert(alleOppgaverNyeOgFerdigstilte.size > 100)
                assert(alleOppgaverNyeOgFerdigstilte.map { it.behandlingType }.distinct().size > 1)
                assert(alleOppgaverNyeOgFerdigstilte.map { it.fagsakYtelseType }.distinct().size > 1)
            }
        }
    }

    @Test
    fun `Hent fagsaker query`() {
        val oppgaveTjeneste = get<OppgaveTjeneste>()

        Kjøretid.logg("Hent beholdning av oppgaver") {
            runBlocking {
                val fagsaker = oppgaveTjeneste.søkFagsaker("Yz647")
                assert(fagsaker.oppgaver.isNotEmpty())
            }
        }
    }


    fun opprettOgFerdigstillOppgave(
        dato: LocalDateTime = LocalDateTime.now(),
        behandlendeEnhet: String = "4409",
        behandlingType: BehandlingType = BehandlingType.FORSTEGANGSSOKNAD,
    ) {
            val datoOpprettet = dato.minusWeeks(1)
            val behandlingsId = UUID.randomUUID()

            val oppgaveRepo = get<OppgaveRepository>()
            val statistikkRepository = get<StatistikkRepository>()

            val oppgave = mockOppgave().copy(
                eksternId = behandlingsId,
                behandlingOpprettet = datoOpprettet,
                oppgaveAvsluttet = dato,
                behandlendeEnhet = behandlendeEnhet
            )
            oppgaveRepo.lagre(behandlingsId) { oppgave }

            statistikkRepository.lagreFerdigstilt(behandlingType.kode, behandlingsId, dato.toLocalDate())

            val nyeOgFerdigstilte = AlleOppgaverNyeOgFerdigstilte(
                oppgave.fagsakYtelseType,
                behandlingType,
                dato.toLocalDate(),
                nye = mutableSetOf(behandlingsId.toString()),
                ferdigstilte = mutableSetOf(behandlingsId.toString()),
                ferdigstilteSaksbehandler = mutableSetOf(oppgave.ansvarligSaksbehandlerIdent!!)
            )
            statistikkRepository.lagre(nyeOgFerdigstilte) { nyeOgFerdigstilte }
    }

    private fun mockOppgave(): Oppgave {
        return Oppgave(
            
            fagsakSaksnummer = "Yz647",
            journalpostId = null,
            aktorId = "273857",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = random(BehandlingType::class.java),
            fagsakYtelseType = random(FagsakYtelseType::class.java),
            aktiv = random.nextBoolean(),
            system = random(Fagsystem::class.java).toString(),
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            ansvarligSaksbehandlerIdent = TestSaksbehandler.tilfeldig().toString()
        )
    }

    fun <T : Enum<*>?> random(clazz: Class<T>): T {
        val x = random.nextInt(clazz.enumConstants.size)
        return clazz.enumConstants[x]
    }

    enum class TestSaksbehandler {
        Z1111111,
        Z2222222,
        Z3333333,
        Z4444444,
        Z5555555;

        companion object {
            val random = Random()

            fun tilfeldig(): TestSaksbehandler {
                val pos = random.nextInt(values().size)
                return values()[pos]
            }
        }
    }


    object Kjøretid {
        private val log = LoggerFactory.getLogger(YtelseTest::class.java)

        fun logg(navn: String = "Jobb", funksjon: () -> Unit) {
            val start = System.currentTimeMillis()
            funksjon()
            log.info("$navn utført på [${start.tidBrukt()} ms]")
        }

        private fun Long.tidBrukt(): Long {
            return System.currentTimeMillis() - this
        }
    }
}
