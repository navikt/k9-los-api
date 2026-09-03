package no.nav.k9.los.infrastruktur.abac.tilganger

import no.nav.k9.los.infrastruktur.idtoken.IdToken

internal interface PdpTilgangsskygge {
    fun observer(idToken: IdToken, autoritative: Tilganger)
}

internal object IngenPdpTilgangsskygge : PdpTilgangsskygge {
    override fun observer(idToken: IdToken, autoritative: Tilganger) = Unit
}
