package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

class GyldigeFeltutledere {
    companion object {
        val feltutledere = hashMapOf(
            AkkumulertVentetidSaksbehandler::class.java.canonicalName to AkkumulertVentetidSaksbehandler(),
            AkkumulertVentetidSøker::class.java.canonicalName to AkkumulertVentetidSøker(),
            AkkumulertVentetidArbeidsgiver::class.java.canonicalName to AkkumulertVentetidArbeidsgiver(),
            AkkumulertVentetidTekniskFeil::class.java.canonicalName to AkkumulertVentetidTekniskFeil(),
            AkkumulertVentetidAnnet::class.java.canonicalName to AkkumulertVentetidAnnet(),
            AkkumulertVentetidAnnetIkkeSaksbehandlingstid::class.java.canonicalName to AkkumulertVentetidAnnetIkkeSaksbehandlingstid()
        )

        fun hentFeltutleder(utleder: String): Feltutleder {
            return feltutledere[utleder] ?: throw IllegalArgumentException("Utleder finnes ikke: $utleder")
        }
    }
}