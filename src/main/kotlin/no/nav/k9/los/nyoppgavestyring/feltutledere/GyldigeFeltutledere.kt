package no.nav.k9.los.nyoppgavestyring.feltutledere

class GyldigeFeltutledere {
    companion object {
        val feltutledere = hashMapOf(
            AkkumulertVentetidSaksbehandler::class.java.canonicalName to AkkumulertVentetidSaksbehandler()
        )
        fun hentFeltutleder(utleder: String) : Feltutleder {
            return feltutledere[utleder] ?: throw IllegalArgumentException("Utleder finnes ikke: $utleder")
        }
    }
}