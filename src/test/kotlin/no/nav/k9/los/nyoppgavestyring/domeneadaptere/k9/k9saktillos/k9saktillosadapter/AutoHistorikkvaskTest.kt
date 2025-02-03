package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9saktillos.k9saktillosadapter

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.BehandlingProsessEventDtoBuilder
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.aksjonspunktbehandling.TestSaksbehandler
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*

class AutoHistorikkvaskTest : AbstractK9LosIntegrationTest() {

    lateinit var eventHandler: K9sakEventHandler
    lateinit var oppgaveKøTjeneste: OppgaveKoTjeneste
    lateinit var oppgaveRepositoryTxWrapper: OppgaveRepositoryTxWrapper

    @BeforeEach
    fun setup() {
        eventHandler = get<K9sakEventHandler>()
        oppgaveKøTjeneste = get<OppgaveKoTjeneste>()
        oppgaveRepositoryTxWrapper = get<OppgaveRepositoryTxWrapper>()
        TestSaksbehandler().init()
        OppgaveTestDataBuilder()
    }

    @Test
    fun `Når k9sakAdapter oppdager eventer i feil rekkefølge skal historikk på oppgaven vaskes automatisk`() {
        val eksternId1 = UUID.randomUUID()

        val saksnummer = "SAKSNUMMER"
        val behandling1 = BehandlingProsessEventDtoBuilder(eksternId1, saksnummer = saksnummer,  pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        val event1 = behandling1.vurderSykdom().build(1)
        val event2 = behandling1.hosBeslutter().build(2)
        val event3 = behandling1.beslutterGodkjent().build(3)
        val event4 = behandling1.avsluttet().build(4)
        eventHandler.prosesser(event1)
        eventHandler.prosesser(event2)
        eventHandler.prosesser(event4)     // Feil rekkefølge i avsluttet behandling fra k9-sak
        eventHandler.prosesser(event3)

        //TODO: Håndtere parallellitet
        K9SakTilLosHistorikkvaskTjeneste(get(),get(),get(),get(),get(),get()).vaskOppgaveForBehandlingUUID(eksternId1)

        val oppgave = oppgaveRepositoryTxWrapper.hentOppgave("K9", eksternId1.toString())
        assertThat(oppgave.status).isEqualTo(Oppgavestatus.LUKKET.kode)
    }
}