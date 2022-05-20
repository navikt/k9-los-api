package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.AbstractPostgresTest
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.AksjonspunktStatus
import no.nav.k9.domene.modell.AksjonspunktTilstand
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class NokkeltallTjenesteTest : KoinTest, AbstractPostgresTest()  {
    @JvmField
    @RegisterExtension
    val koinTestRule = KoinTestExtension.create {
        modules(buildAndTestConfig(dataSource))
    }

    val DAG1 = LocalDate.now().minusDays(2)
    val DAG2 = LocalDate.now().minusDays(1)
    val DAG3 = LocalDate.now()

    @Test
    fun `Hent oppgaver på vent - 1 oppgave uten aksjonspunkt skal gi 0 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()

        //Lag en oppgave uten aksjonspunkt
        opprettOppgave(mapOf())

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVentV2().påVent

        assert(oppgaverPåVent.isEmpty())
    }

    @Test
    fun `Hent oppgaver på vent - 1 oppgave med aksjonspunkt som ikke er autopunkt skal gi 0 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()

        //Lag en oppgave uten aksjonspunkt
        opprettOppgave(mapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to "OPPR"))

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVentV2().påVent

        assert(oppgaverPåVent.isEmpty())
    }

    @Test
    fun `Hent oppgaver på vent - 1 oppgave med aksjonspunkt som er autopunkt skal gi 1 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()

        //Lag en oppgave uten aksjonspunkt
        opprettOppgave(mapOf(AksjonspunktDefinisjon.AUTO_MANUELT_SATT_PÅ_VENT.kode to "OPPR"))

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVentV2().påVent

        assert(oppgaverPåVent.size == 1)
        assert(oppgaverPåVent[0].behandlingType == BehandlingType.FORSTEGANGSSOKNAD)
        assert(oppgaverPåVent[0].fagsakYtelseType == FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        assert(oppgaverPåVent[0].antall == 1)
    }

    @Test
    fun `Hent løste aksjonspunkter bortsett fra de med behandlende enhet 2103`() {
        val statistikkRepository = mockk<StatistikkRepository>()
        val nøkkeltallTjeneste = NokkeltallTjeneste(mockk(), statistikkRepository)

        val ferdigstiltBehandling = FerdigstiltBehandling(
            dato = LocalDate.now(),
            fagsakYtelseType = "PSB",
            behandlingType = "BT-002",
            fagsystemType = "K9SAK",
            saksbehandler = "Z12345",
            behandlendeEnhet = "4565 OK AVDELING"
        )

        every { statistikkRepository.hentFerdigstiltOppgavehistorikk(any()) } returns listOf(
            ferdigstiltBehandling,
            ferdigstiltBehandling.copy(behandlendeEnhet = "2103 VIKEN")
        )

        val ferdigstilteoppgaver = nøkkeltallTjeneste.hentFerdigstilteBehandlingerPrEnhetHistorikk()

        assertThat(ferdigstilteoppgaver).containsOnly(ferdigstiltBehandling.dato to mapOf("4565 OK AVDELING" to 1))
        assertThat(ferdigstilteoppgaver).doesNotContain(ferdigstiltBehandling.dato to mapOf("2103 VIKEN" to 1))
    }

    private fun opprettOppgave(aksjonspunkter: Map<String, String>) {
        val oppgaveRepo = get<OppgaveRepository>()
        val oppgave = mockOppgave().copy(aksjonspunkter = Aksjonspunkter(aksjonspunkter, aksjonspunkter.map { AksjonspunktTilstand(it.key, AksjonspunktStatus.fraKode(it.value)) }))
        oppgaveRepo.lagre(oppgave.eksternId) {oppgave}
    }

    private fun mockOppgave(): Oppgave {
        return Oppgave(
            eksternId = UUID.randomUUID(),
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
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
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
