package no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.kontekst.medBrukerkontekst
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.koin.ktor.ext.inject

fun Route.NyeOgFerdigstilteApiNy() {
    val pepClient by inject<IPepClient>()
    val nyeOgFerdigstilteService by inject<NyeOgFerdigstilteService>()

    get({
        description = "Hent antall nye og ferdigstilte oppgaver for innlogget saksbehandler, eventuelt filtrert på ytelsesgruppe."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            queryParameter<NyeOgFerdigstilteGruppe>("gruppe") {
                description = "Ytelsesgruppe å filtrere på. Dersom utelatt returneres tall for alle ytelser."
                required = false
                example("ALLE") { value = NyeOgFerdigstilteGruppe.ALLE }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (with(kontekst) { pepClient.harBasisTilgang() }) {
                call.respond(nyeOgFerdigstilteService.hentCachetVerdi(call.parameters["gruppe"]?.let { NyeOgFerdigstilteGruppe.valueOf(it) } ?: NyeOgFerdigstilteGruppe.ALLE))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}

