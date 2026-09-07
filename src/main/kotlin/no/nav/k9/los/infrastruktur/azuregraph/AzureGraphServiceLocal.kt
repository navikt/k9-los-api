package no.nav.k9.los.infrastruktur.azuregraph

import no.nav.k9.los.infrastruktur.idtoken.IdToken

open class AzureGraphServiceLocal : IAzureGraphService {
    override suspend fun hentEnhet(navIdent: String, idToken: IdToken): String {
        return "3450"
    }
}
