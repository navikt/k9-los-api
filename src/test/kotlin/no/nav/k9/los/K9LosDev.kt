package no.nav.k9.los

import io.ktor.server.testing.*

class K9LosDev {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val testArgs = TestConfiguration.asMap()
            testApplication {
                no.nav.k9.los.main(testArgs.asArguments())
            }
        }

        private fun Map<String, String>.asArguments(): Array<String> =
            flatMap { (key, value) -> listOf("-P:$key=$value") }.toTypedArray()
    }
}
