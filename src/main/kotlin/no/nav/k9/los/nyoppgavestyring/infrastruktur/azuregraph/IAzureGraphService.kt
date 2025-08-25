package no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph

import java.util.*

interface IAzureGraphService {

    suspend fun hentIdentTilInnloggetBruker(): String

    suspend fun hentEnhetForInnloggetBruker(): String

    suspend fun hentEnhetForBrukerMedSystemToken(brukernavn: String): String?

    suspend fun hentGrupperForSaksbehandler(saksbehandlerIdent: String): Set<UUID>

    suspend fun hentGrupperForInnloggetSaksbehandler(): Set<UUID>
}