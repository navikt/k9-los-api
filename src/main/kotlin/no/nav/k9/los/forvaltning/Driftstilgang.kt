package no.nav.k9.los.forvaltning

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.kodeverk.Fagsystem
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

internal suspend fun RoutingContext.medDriftstilgang(block: suspend (BrukerkontekstMedOmråde) -> Unit) {
    medBrukerkontekst { bruker ->
        if (!bruker.harDriftstilgang) {
            call.respond(HttpStatusCode.Forbidden)
            return@medBrukerkontekst
        }
        try {
            block(bruker)
        } catch (_: SecurityException) {
            call.respond(HttpStatusCode.Forbidden)
        }
    }
}

internal fun BrukerkontekstMedOmråde.krevK9Drift() {
    if (!harDriftstilgang || område != Områder.K9) throw SecurityException("Krever K9-driftstilgang")
}

internal fun BrukerkontekstMedOmråde.krevFagsystem(fagsystem: Fagsystem) {
    // UNG har ikke et definert område i Områder og må ikke falle tilbake til K9 eller AP.
    val faktiskOmråde = when (fagsystem) {
        Fagsystem.K9SAK, Fagsystem.K9TILBAKE, Fagsystem.K9KLAGE, Fagsystem.PUNSJ -> Områder.K9
        Fagsystem.UNGSAK, Fagsystem.UNGTILBAKE -> null
    }
    if (!harDriftstilgang || faktiskOmråde != område) throw SecurityException("Ingen tilgang til fagsystem")
}
