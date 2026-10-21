package no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte

import kotlinx.coroutines.CoroutineScope
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeDispatcher
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class InngangOgFerdigstilteService(
    k9: K9InngangOgFerdigstilteService,
    aktivitetspenger: AktivitetspengerInngangOgFerdigstilteService,
) : OmrådeDispatcher<InngangOgFerdigstilte>(k9, aktivitetspenger) {
    fun hentCachetVerdi(område: Områder): InngangOgFerdigstilteResponse =
        forOmråde(område).hentCachetVerdi()

    fun oppdaterCache(område: Områder, scope: CoroutineScope) =
        forOmråde(område).oppdaterCache(scope)
}
