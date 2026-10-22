package no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte

import kotlinx.coroutines.CoroutineScope
import no.nav.k9.los.nøkkeltall.KodeOgNavn
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeDispatcher
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class NyeOgFerdigstilteService(
    k9: K9NyeOgFerdigstilteAdapter,
    aktivitetspenger: AktivitetspengerNyeOgFerdigstilteService,
) : OmrådeDispatcher<NyeOgFerdigstilte>(k9, aktivitetspenger) {
    fun grupper(område: Områder): List<KodeOgNavn> = forOmråde(område).grupper

    fun hentCachetVerdi(område: Områder, gruppe: String): NyeOgFerdigstilteResponse =
        forOmråde(område).hentCachetVerdi(gruppe)

    fun oppdaterCache(område: Områder, scope: CoroutineScope) =
        forOmråde(område).oppdaterCache(scope)
}
