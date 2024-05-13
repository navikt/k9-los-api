package no.nav.k9.los.nyoppgavestyring.reservasjon

data class Reservasjonsnøkkel(
    val nøkkel: String
) {
    override fun toString(): String {
        return if (nøkkel.contains("beslutter")) {
            "beslutter"
        } else if (nøkkel.contains("legacy")) {
            "legacy"
        } else { "ordinær" }
    }
}