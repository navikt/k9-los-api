package no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte

import kotlinx.coroutines.CoroutineScope

interface InngangOgFerdigstilte {
    fun hentCachetVerdi(): InngangOgFerdigstilteResponse
    fun oppdaterCache(scope: CoroutineScope)
}
