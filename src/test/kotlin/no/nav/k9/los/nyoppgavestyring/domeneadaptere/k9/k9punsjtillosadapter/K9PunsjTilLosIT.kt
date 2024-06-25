package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9punsjtillosadapter

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.los.aksjonspunktbehandling.PunsjEventDtoBuilder
import no.nav.k9.los.aksjonspunktbehandling.TestSaksbehandler
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveApisTjeneste
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*


class K9PunsjTilLosIT : AbstractK9LosIntegrationTest() {

    lateinit var eventHandler: K9punsjEventHandler
    lateinit var oppgaveKøTjeneste: OppgaveKoTjeneste

    lateinit var oppgaveApisTjeneste: OppgaveApisTjeneste

    @BeforeEach
    fun setup() {
        eventHandler = get<K9punsjEventHandler>()

        oppgaveKøTjeneste = get<OppgaveKoTjeneste>()
        oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        TestSaksbehandler().init()
        OppgaveTestDataBuilder()
    }

    @Test
    fun `Punsjadapter skal håndtere eventer fra punsj som enda ikke er klassifisert til noen ytelse`() {
        val punsjEventDtoBuilder = PunsjEventDtoBuilder(ytelse = FagsakYtelseType.UKJENT)
        eventHandler.prosesser(punsjEventDtoBuilder.papirsøknad().build())

        // Sjekke at de dukker opp i oppgavequery
    }
}