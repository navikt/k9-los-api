package no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet

import no.nav.k9.los.kodeverk.BehandlendeEnhet

sealed class FerdigstiltParameter(val navn: String) {
    data class Enhet(val enhet: BehandlendeEnhet) : FerdigstiltParameter("${enhet.kode} ${enhet.navn}")
    data object Helautomatisk : FerdigstiltParameter("Helautomatisk behandlet")
    data object Andre : FerdigstiltParameter("Ukjent enhet")
}
