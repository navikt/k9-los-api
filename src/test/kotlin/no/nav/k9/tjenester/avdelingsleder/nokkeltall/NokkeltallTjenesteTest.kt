package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.repository.OppgaveRepository
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
        opprettOppgave(mapOf(AksjonspunktDefinisjon.FATTER_VEDTAK.kode to "OPPR"))

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVent()

        assert(oppgaverPåVent.isEmpty())
    }

    @Test
    fun `Hent oppgaver på vent - 1 oppgave med aksjonspunkt som er autopunkt skal gi 1 oppgaver på vent`() {
        val nøkkeltallTjeneste = get<NokkeltallTjeneste>()

        //Lag en oppgave uten aksjonspunkt
        opprettOppgave(mapOf(AksjonspunktDefinisjon.AUTO_MANUELT_SATT_PÅ_VENT.kode to "OPPR"))

        val oppgaverPåVent = nøkkeltallTjeneste.hentOppgaverPåVent()

        assert(oppgaverPåVent.size == 1)
        assert(oppgaverPåVent[0].behandlingType == BehandlingType.FORSTEGANGSSOKNAD)
        assert(oppgaverPåVent[0].fagsakYtelseType == FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        assert(oppgaverPåVent[0].antall == 1)
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
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
    }

}