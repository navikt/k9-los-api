package no.nav.k9.los.nyoppgavestyring.transientfeltutleder

import no.nav.k9.los.spi.felter.TransientFeltutleder

class GyldigeTransientFeltutleder {
    /*
     * Denne kan erstattes av ServiceLoader eller tilsvarende.
     */

    companion object {
        val feltutledere: Map<String, TransientFeltutleder> = hashMapOf(
            K9SakBeslutterTransientFeltutleder::class.java.canonicalName to K9SakBeslutterTransientFeltutleder(),
            K9SakOppgavesaksbehandlingstidUtleder::class.java.canonicalName to K9SakOppgavesaksbehandlingstidUtleder(),
        )

        fun hentFeltutleder(utleder: String): TransientFeltutleder {
            return feltutledere[utleder] ?: throw IllegalArgumentException("TransientFeltutleder finnes ikke: $utleder")
        }
    }
}