package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

class GyldigeFeltutledere {
    companion object {
        val feltutledere = hashMapOf(
            AkkumulertVentetidSaksbehandler::class.java.canonicalName to AkkumulertVentetidSaksbehandler(),
            AkkumulertVentetidSøker::class.java.canonicalName to AkkumulertVentetidSøker(),
            AkkumulertVentetidArbeidsgiver::class.java.canonicalName to AkkumulertVentetidArbeidsgiver(),
            AkkumulertVentetidTekniskFeil::class.java.canonicalName to AkkumulertVentetidTekniskFeil()
        )

        fun hentFeltutleder(utleder: String): Feltutleder {
            return feltutledere[utleder] ?: throw IllegalArgumentException("Utleder finnes ikke: $utleder")
        }
    }
}