package no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte

import kotlinx.coroutines.CoroutineScope
import no.nav.k9.los.nøkkeltall.KodeOgNavn

interface NyeOgFerdigstilte {
    val grupper: List<KodeOgNavn>
    fun hentCachetVerdi(gruppe: String): NyeOgFerdigstilteResponse
    fun oppdaterCache(scope: CoroutineScope)
}
