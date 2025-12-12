package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlendeEnhet

sealed class FerdigstiltParameter(val navn: String) {
    data class Enhet(val enhet: BehandlendeEnhet) : FerdigstiltParameter("${enhet.kode} ${enhet.navn}")
    data object Helautomatisk : FerdigstiltParameter("Helautomatisk behandlet")
    data object Andre : FerdigstiltParameter("Ukjent enhet")
}
