package no.nav.k9.los.infrastruktur.azuregraph

import no.nav.k9.los.infrastruktur.kontekst.InnloggetBruker
import java.util.*

interface IAzureGraphService {

    suspend fun hentIdentTilInnloggetBruker(bruker: InnloggetBruker): String

    suspend fun hentEnhetForInnloggetBruker(bruker: InnloggetBruker): String

    suspend fun hentEnhetForBrukerMedSystemToken(brukernavn: String): String?

    suspend fun hentGrupperForSaksbehandler(saksbehandlerIdent: String): Set<UUID>

    suspend fun hentGrupperForInnloggetSaksbehandler(bruker: InnloggetBruker): Set<UUID>
}