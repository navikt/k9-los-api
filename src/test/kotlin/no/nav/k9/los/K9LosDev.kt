package no.nav.k9.los

class K9LosDev {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val testArgs = TestConfiguration.asArguments()
            no.nav.k9.los.main(testArgs + args)
        }
    }
}
