package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9tilbaketillos.k9tilbaketillosadapter

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.*
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Dto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.tjenester.saksbehandler.oppgave.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


class K9TilbakeTilLosIT : AbstractK9LosIntegrationTest() {

    lateinit var eventHandler: K9TilbakeEventHandler
    lateinit var oppgaveKøTjeneste: OppgaveKoTjeneste

    lateinit var oppgaveApisTjeneste: OppgaveApisTjeneste

    @BeforeEach
    fun setup() {
        eventHandler = get<K9TilbakeEventHandler>()
        oppgaveKøTjeneste = get<OppgaveKoTjeneste>()
        oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        TestSaksbehandler().init()
        OppgaveTestDataBuilder()
    }

    @Test
    fun `Både saksbehandler og beslutters har hver sin reservasjon og saken kan bli sendt i retur`() {
        val eksternId = UUID.randomUUID()
        val eventBuilder = BehandlingProsessEventTilbakeDtoBuilder(eksternId)

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
            get<OppgaveApisTjeneste>().reserverOppgave(
                saksbehandler, OppgaveIdMedOverstyringDto(
                    OppgaveNøkkelDto.forV1Oppgave(eksternId.toString())
                )
            )
        }
    }

    private fun assertIngenReservasjon(saksbehandler: Saksbehandler) {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        runBlocking { assertThat(
            oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(saksbehandler)
        ).isEmpty() }
    }

    private fun assertReservasjon(saksbehandler: Saksbehandler, antallReserverteOppgaver: Int) {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        val reservasjon = runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(saksbehandler) }
        assertThat(reservasjon).isNotEmpty()
        assertThat(reservasjon).hasSize(1)
        reservasjon.first().let {
            assertThat(it.reserverteV3Oppgaver).hasSize(antallReserverteOppgaver)
            it.reserverteV3Oppgaver.forEach {oppgave -> assertThat(oppgave.oppgaveNøkkel.oppgaveTypeEksternId).isEqualTo("k9tilbake")}
        }
    }
}
