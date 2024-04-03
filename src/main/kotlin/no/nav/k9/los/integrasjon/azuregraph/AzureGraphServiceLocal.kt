package no.nav.k9.los.integrasjon.azuregraph

open class AzureGraphServiceLocal : IAzureGraphService {

    override suspend fun hentIdentTilInnloggetBruker(): String {
        return "Z123456"
    }

    override suspend fun hentEnhetForInnloggetBruker(): String {
        return "3450"
    }

    override suspend fun hentEnhetForBrukerMedSystemToken(brukernavn: String): String {
        return "3450"
    }

}
