package no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet

import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9BehandlendeEnhet

sealed class FerdigstiltParameter(val navn: String) {
    data class Enhet(val enhet: K9BehandlendeEnhet) : FerdigstiltParameter("${enhet.kode} ${enhet.navn}")
    data object Helautomatisk : FerdigstiltParameter("Helautomatisk behandlet")
    data object Andre : FerdigstiltParameter("Ukjent enhet")
}
