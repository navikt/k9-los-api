package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.K9TilbakeEventDtoBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.TestSaksbehandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventHandler
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.reservasjon.OppgaveIdMedOverstyringDto
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonApisTjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*


class K9TilbakeTilLosIT : AbstractK9LosIntegrationTest() {

    lateinit var eventHandler: K9TilbakeEventHandler
    lateinit var oppgaveKøTjeneste: OppgaveKoTjeneste

    lateinit var reservasjonApisTjeneste: ReservasjonApisTjeneste

    @BeforeEach
    fun setup() {
        eventHandler = get<K9TilbakeEventHandler>()
        oppgaveKøTjeneste = get<OppgaveKoTjeneste>()
        reservasjonApisTjeneste = get<ReservasjonApisTjeneste>()
        TestSaksbehandler().init()
        OppgaveTestDataBuilder()
    }

    @Test
    fun `Både saksbehandler og beslutters har hver sin reservasjon og saken kan bli sendt i retur`() {
        val eksternId = UUID.randomUUID()
        val eventBuilder = K9TilbakeEventDtoBuilder(eksternId)

        // Åpen oppgave plukkes av saksbehandler
        eventHandler.prosesser(eventBuilder.opprettet().build())
        taReservasjon(TestSaksbehandler.SARA, eksternId)
        assertReservasjon(TestSaksbehandler.SARA, 1)

        eventHandler.prosesser(eventBuilder.foreslåVedtak().build())
        assertReservasjon(TestSaksbehandler.SARA, 1)

        // Behandling sendt til beslutter, beslutter plukken oppgaven
        eventHandler.prosesser(eventBuilder.hosBeslutter().build())
        assertReservasjon(TestSaksbehandler.SARA, 0)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        taReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, eksternId)
        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Beslutter sender oppgaven tilbake til saksbehandler
        eventHandler.prosesser(eventBuilder.returFraBeslutter().build())
        assertReservasjon(TestSaksbehandler.SARA, 1)
        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 0)

        eventHandler.prosesser(eventBuilder.foreslåVedtak().build())

        // Behandlingen sendes til ny beslutning
        eventHandler.prosesser(eventBuilder.hosBeslutter().build())
        assertReservasjon(TestSaksbehandler.SARA, 0)
        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Oppgaven er avsluttet, begge reservasjonen skal annulleres
        eventHandler.prosesser(eventBuilder.avsluttet().build())
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
    }

    private fun taReservasjon(saksbehandler: Saksbehandler, eksternId: UUID) {
        runBlocking {
            get<ReservasjonApisTjeneste>().reserverOppgave(
                saksbehandler, OppgaveIdMedOverstyringDto(
                    OppgaveNøkkelDto.forV1Oppgave(eksternId.toString())
                )
            )
        }
    }

    private fun assertIngenReservasjon(saksbehandler: Saksbehandler) {
        val reservasjonApisTjeneste = get<ReservasjonApisTjeneste>()
        runBlocking { assertThat(
            reservasjonApisTjeneste.hentReserverteOppgaverForSaksbehandler(saksbehandler)
        ).isEmpty() }
    }

    private fun assertReservasjon(saksbehandler: Saksbehandler, antallReserverteOppgaver: Int) {
        val reservasjonApisTjeneste = get<ReservasjonApisTjeneste>()
        val reservasjon = runBlocking { reservasjonApisTjeneste.hentReserverteOppgaverForSaksbehandler(saksbehandler) }
        assertThat(reservasjon).isNotEmpty()
        assertThat(reservasjon).hasSize(1)
        reservasjon.first().let {
            assertThat(it.reserverteV3Oppgaver).hasSize(antallReserverteOppgaver)
            it.reserverteV3Oppgaver.forEach {oppgave -> assertThat(oppgave.oppgaveNøkkel.oppgaveTypeEksternId).isEqualTo("k9tilbake")}
        }
    }
}
