package no.nav.k9.los.tjenester.mock

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.oppgavemottak.OppgaveV3Tjeneste
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.test.get
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.Områdesetup as AktOmrådesetup

class LocalSetupAktivitetspengerTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `mockdata for aktivitetspenger blir til oppgaver`() {
        get<AktOmrådesetup>().setup()

        localSetup.initAktivitetspengeroppgaver(20)

        val (antallAlle, _) = get<OppgaveV3Tjeneste>().tellAntall()
        assertTrue(antallAlle >= 20) { "Forventet minst 20 oppgaver, fant $antallAlle" }
    }
}

