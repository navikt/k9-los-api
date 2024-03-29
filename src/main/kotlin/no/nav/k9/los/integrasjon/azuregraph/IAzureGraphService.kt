package no.nav.k9.los.integrasjon.azuregraph

interface IAzureGraphService {

    suspend fun hentIdentTilInnloggetBruker(): String

    suspend fun hentEnhetForInnloggetBruker(): String

    suspend fun hentEnhetForBrukerMedSystemToken(brukernavn: String): String?
}