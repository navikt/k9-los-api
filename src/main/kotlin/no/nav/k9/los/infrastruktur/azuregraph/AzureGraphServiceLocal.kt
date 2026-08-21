package no.nav.k9.los.infrastruktur.azuregraph

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import java.util.*

open class AzureGraphServiceLocal : IAzureGraphService {
    override suspend fun hentEnhet(kontekst: BrukerkontekstMedOmråde): String {
        return "3450"
    }

    override suspend fun hentEnhet(brukernavn: String): String {
        return "3450"
    }

    override suspend fun hentGrupper(brukerIdent: String): Set<UUID> {
        return emptySet()
    }

    override suspend fun hentGrupper(kontekst: BrukerkontekstMedOmråde): Set<UUID> {
        return emptySet()
    }
}