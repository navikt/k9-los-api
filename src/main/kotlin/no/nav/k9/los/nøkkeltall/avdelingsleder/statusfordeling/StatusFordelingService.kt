package no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling

import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeDispatcher
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class StatusFordelingService(
    k9: K9StatusFordelingService,
    aktivitetspenger: AktivitetspengerStatusFordelingService,
) : OmrådeDispatcher<StatusFordeling>(k9, aktivitetspenger) {
    fun hentVerdi(område: Områder, kode6: Boolean): StatusFordelingResponse =
        forOmråde(område).hentVerdi(kode6)

    fun oppdaterCache(område: Områder, kode6: Boolean) =
        forOmråde(område).oppdaterCache(kode6)
}
