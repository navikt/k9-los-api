package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

sealed class FerdigstiltParameter(val navn: String) {
    data class Enhet(val enhet: String) : FerdigstiltParameter(enhet)
    data object Helautomatisk : FerdigstiltParameter("Helautomatisk behandlet")
}
