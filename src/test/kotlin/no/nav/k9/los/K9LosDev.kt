package no.nav.k9.los

import no.nav.helse.dusseldorf.testsupport.asArguments

class K9LosDev {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val testArgs = TestConfiguration.asMap() + mapOf(
                "nav.testdata.enabled" to "true",
                "ktor.deployment.host" to "127.0.0.1",
            )
            no.nav.k9.los.main(testArgs.asArguments() + args)
        }
    }
}
