package no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte

import kotlinx.coroutines.CoroutineScope
import no.nav.k9.los.nøkkeltall.KodeOgNavn

class K9NyeOgFerdigstilteAdapter(private val kilde: K9NyeOgFerdigstilteService) : NyeOgFerdigstilte {
    override val grupper = NyeOgFerdigstilteGruppe.entries.map { KodeOgNavn(it.name, it.navn) }

    override fun hentCachetVerdi(gruppe: String): NyeOgFerdigstilteResponse =
        kilde.hentCachetVerdi(NyeOgFerdigstilteGruppe.valueOf(gruppe))

    override fun oppdaterCache(scope: CoroutineScope) = kilde.oppdaterCache(scope)
}
