package no.nav.k9.los.infrastruktur.abac.tilganger

import no.nav.k9.los.infrastruktur.idtoken.IIdToken

internal fun interface TilgangerKlient {
    suspend fun hent(idToken: IIdToken): PdpResultat
}