package no.nav.k9.los

import io.ktor.server.config.*
import io.ktor.server.testing.*

class K9LosDev {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val testArgs = TestConfiguration.asMap().toList()
            testApplication {
                environment {
                    config = MapApplicationConfig(testArgs).withFallback(ApplicationConfig("application.conf"))
                }
            }
        }
    }
}
