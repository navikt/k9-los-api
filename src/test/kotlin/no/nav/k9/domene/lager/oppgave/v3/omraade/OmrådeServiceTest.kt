package no.nav.k9.domene.lager.oppgave.v3.omraade

import no.nav.k9.AbstractK9LosIntegrationTest
import org.junit.jupiter.api.Test
import org.koin.test.get

class OmrådeServiceTest: AbstractK9LosIntegrationTest() {

    @Test
    fun test() {
        val områdeService = get<OmrådeService>()

        val område = områdeService.hentOmrådeFraFil()
        println("Stopp")
    }


}