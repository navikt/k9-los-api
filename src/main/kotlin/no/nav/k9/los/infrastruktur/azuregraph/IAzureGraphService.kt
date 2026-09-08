package no.nav.k9.los.infrastruktur.azuregraph

import java.util.*

interface IAzureGraphService {
    suspend fun hentEnhetForInnloggetBruker(): String
    suspend fun hentGrupperForSaksbehandler(saksbehandlerIdent: String): Set<UUID>
}