package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class NokkeltallTjenesteTest : KoinTest {
    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(buildAndTestConfig())
    }

    val DAG1 = LocalDate.now().minusDays(2)
    val DAG2 = LocalDate.now().minusDays(1)
    val DAG3 = LocalDate.now()

    @Test
    fun `Hent oppgaver på vent - 1 oppgave uten aksjonspunkt skal gi 0 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()

        //Lag en oppgave uten aksjonspunkt
        opprettOppgave(mapOf())

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVent()

        assert(oppgaverPåVent.isEmpty())
    }

    @Test
    fun `Hent oppgaver på vent - 1 oppgave med aksjonspunkt som ikke er autopunkt skal gi 0 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()

        //Lag en oppgave uten aksjonspunkt
        opprettOppgave(mapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to AksjonspunktStatus.OPPRETTET.kode))

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVent()

        assert(oppgaverPåVent.isEmpty())
    }

    @Test
    fun `Hent oppgaver på vent - 1 oppgave med aksjonspunkt som er autopunkt skal gi 1 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()

        //Lag en oppgave uten aksjonspunkt
        opprettOppgave(mapOf(AksjonspunktDefinisjon.AUTO_MANUELT_SATT_PÅ_VENT.kode to AksjonspunktStatus.OPPRETTET.kode))

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVent()

        assert(oppgaverPåVent.size == 1)
        assert(oppgaverPåVent[0].behandlingType == BehandlingType.FORSTEGANGSSOKNAD)
        assert(oppgaverPåVent[0].fagsakYtelseType == FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        assert(oppgaverPåVent[0].antall == 1)
    }

    @Test
    fun `Hent historikk for ferdigstilte per enhet som aggreggerer antall og fyller tomrom i datasettet med tom liste`() {

        val statistikkRepository = mockk<StatistikkRepository>()
        every { statistikkRepository.hentFerdigstiltOppgavehistorikk() } returns listOf(
            FerdigstiltBehandling(dato = DAG1, behandlendeEnhet = "1111"),
            FerdigstiltBehandling(dato = DAG1, behandlendeEnhet = "2222"),
            FerdigstiltBehandling(dato = DAG1, behandlendeEnhet = "2222"),
            // TIRSDAG - Ingen behandlinger
            FerdigstiltBehandling(dato = DAG3, behandlendeEnhet = "1111"),
            FerdigstiltBehandling(dato = DAG3, behandlendeEnhet = "2222"),
            FerdigstiltBehandling(dato = DAG3, behandlendeEnhet = "3333"),
        )

        val nøkkeltallTjeneste = NokkeltallTjeneste(mockk(), statistikkRepository)
        val historikk = nøkkeltallTjeneste.hentFerdigstilteOppgavePrEnhetHistorikk()
        assertThat(historikk).isEqualTo(
            mapOf(
                DAG1 to mapOf(
                    "1111" to 1,
                    "2222" to 2
                ),
                DAG2 to emptyMap(),
                DAG3 to mapOf(
                    "1111" to 1,
                    "2222" to 1,
                    "3333" to 1
                ),
            )
        )
    }

    private fun opprettOppgave(aksjonspunkter: Map<String, String>) {
        val oppgaveRepo = get<OppgaveRepository>()
        val oppgave = mockOppgave().copy(aksjonspunkter = Aksjonspunkter(aksjonspunkter))
        oppgaveRepo.lagre(oppgave.eksternId) {oppgave}
    }

    private fun mockOppgave(): Oppgave {
        return Oppgave(
            behandlingId = 9438,
            fagsakSaksnummer = "Yz647",
            journalpostId = null,
            aktorId = "273857",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.FORSTEGANGSSOKNAD,
            fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            aktiv = true,
            system = "K9SAK",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            ansvarligSaksbehandlerIdent = "Z123523",
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
    }

}
