package no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph

interface IAzureGraphService {

    suspend fun hentIdentTilInnloggetBruker(): String

    suspend fun hentEnhetForInnloggetBruker(): String

    suspend fun hentEnhetForBrukerMedSystemToken(brukernavn: String): String?
}