package no.nav.k9.los.infrastruktur.azuregraph

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import java.util.*

interface IAzureGraphService {
    suspend fun hentEnhet(kontekst: BrukerkontekstMedOmråde): String
    suspend fun hentEnhet(brukerIdent: String): String?
    suspend fun hentGrupper(kontekst: BrukerkontekstMedOmråde): Set<UUID>
    suspend fun hentGrupper(brukerIdent: String): Set<UUID>
}