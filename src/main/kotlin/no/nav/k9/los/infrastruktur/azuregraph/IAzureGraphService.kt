package no.nav.k9.los.infrastruktur.azuregraph

import no.nav.k9.los.infrastruktur.idtoken.IdToken

interface IAzureGraphService {
    suspend fun hentEnhet(navIdent: String, idToken: IdToken): String
}
