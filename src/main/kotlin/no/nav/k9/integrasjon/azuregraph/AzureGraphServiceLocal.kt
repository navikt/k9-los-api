package no.nav.k9.integrasjon.azuregraph

open class AzureGraphServiceLocal constructor() : IAzureGraphService {

    override suspend fun hentIdentTilInnloggetBruker(): String {
        return "saksbehandler@nav.no"
    }

    override suspend fun hentEnhetForInnloggetBruker(): String {
        return "saksbehandler@nav.no"
    }

}