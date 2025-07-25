package no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph

import java.util.*

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

    override suspend fun hentGrupperForSaksbehandler(saksbehandlerIdent: String): Set<UUID> {
        return emptySet()
    }

    override suspend fun hentGrupperForInnloggetSaksbehandler(): Set<UUID> {
        return emptySet()
    }
}
