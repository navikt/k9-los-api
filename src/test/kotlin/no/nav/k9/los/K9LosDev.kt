package no.nav.k9.los

import io.ktor.server.testing.*
import no.nav.helse.dusseldorf.testsupport.asArguments

class K9LosDev {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val testArgs = TestConfiguration.asMap()
            testApplication {
                no.nav.k9.los.main(testArgs.asArguments())
            }
        }
    }
}
