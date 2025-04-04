package no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph

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
