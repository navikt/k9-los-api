package no.nav.k9.los.infrastruktur.azuregraph

import no.nav.k9.los.infrastruktur.kontekst.InnloggetBruker
import java.util.*

open class AzureGraphServiceLocal : IAzureGraphService {

    override suspend fun hentIdentTilInnloggetBruker(bruker: InnloggetBruker): String {
        return "Z123456"
    }

    override suspend fun hentEnhetForInnloggetBruker(bruker: InnloggetBruker): String {
        return "3450"
    }

    override suspend fun hentEnhetForBrukerMedSystemToken(brukernavn: String): String {
        return "3450"
    }

    override suspend fun hentGrupperForSaksbehandler(saksbehandlerIdent: String): Set<UUID> {
        return emptySet()
    }

    override suspend fun hentGrupperForInnloggetSaksbehandler(bruker: InnloggetBruker): Set<UUID> {
        return emptySet()
    }
}