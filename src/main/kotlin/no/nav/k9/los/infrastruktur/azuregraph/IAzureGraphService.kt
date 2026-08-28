package no.nav.k9.los.infrastruktur.azuregraph

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import java.util.*

interface IAzureGraphService {
    suspend fun hentEnhet(brukerkontekst: BrukerkontekstMedOmråde): String
    suspend fun hentGrupper(navIdent: String): Set<UUID>
}