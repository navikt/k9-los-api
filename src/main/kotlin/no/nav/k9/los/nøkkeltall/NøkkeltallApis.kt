package no.nav.k9.los.nøkkeltall

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.infrastruktur.utils.IkkeImplementertException
import no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte.InngangOgFerdigstilteResponse
import no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte.InngangOgFerdigstilteService
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetGruppe
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetResponse
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.K9FerdigstiltePerEnhetService
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingResponse
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.koin.ktor.ext.inject

fun Route.NøkkeltallApis() {
    val statusFordelingService by inject<StatusFordelingService>()
    val inngangOgFerdigstilteService by inject<InngangOgFerdigstilteService>()
    val perEnhetService by inject<K9FerdigstiltePerEnhetService>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get("statusfordeling", {
        operationId = "hentStatusfordeling"
        summary = "Hent statusfordeling for området"
        description = "K9 har statuskort per behandlingsgruppe. Aktivitetspenger har behandlinger samlet, del 1 og del 2."
        response {
            HttpStatusCode.OK to { body<StatusFordelingResponse>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler tilgang til oppgavestyring i området" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(statusFordelingService.hentVerdi(coroutineContext.område(), pepClient.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("inngang-og-ferdigstilte", {
        operationId = "hentInngangOgFerdigstilte"
        summary = "Hent inngang og ferdigstilte for området"
        description = "Antall mottatte og ferdigstilte oppgaver i dag og siste én, to og fire uker. Grupper varierer per område. Aktivitetspenger har ikke manuelt/automatisk-fordeling."
        response {
            HttpStatusCode.OK to { body<InngangOgFerdigstilteResponse>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler tilgang til oppgavestyring i området" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(inngangOgFerdigstilteService.hentCachetVerdi(coroutineContext.område()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("ferdigstilte-per-enhet", {
        operationId = "hentFerdigstiltePerEnhet"
        summary = "Hent antall ferdigstilte behandlinger per enhet"
        description = "Støttes foreløpig bare for K9."
        request {
            queryParameter<FerdigstiltePerEnhetGruppe>("gruppe") { description = "Behandlingsgruppe, standard ALLE" }
            queryParameter<Int>("uker") { description = "Antall uker, 2 eller 4. Standard 2." }
        }
        response {
            HttpStatusCode.OK to { body<FerdigstiltePerEnhetResponse>() }
            HttpStatusCode.BadRequest to { description = "Ugyldig gruppe eller antall uker" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler tilgang til oppgavestyring i området" }
            HttpStatusCode.NotImplemented to { description = "Nøkkeltallet støttes ikke for området" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                krevK9(coroutineContext.område())
                val gruppeParameter = call.request.queryParameters["gruppe"]
                val gruppe = if (gruppeParameter == null) FerdigstiltePerEnhetGruppe.ALLE
                else FerdigstiltePerEnhetGruppe.entries.find { it.name == gruppeParameter }
                val ukerParameter = call.request.queryParameters["uker"]
                val uker = if (ukerParameter == null) 2 else ukerParameter.toIntOrNull()
                if (gruppe == null || uker !in listOf(2, 4)) {
                    call.respond(HttpStatusCode.BadRequest, "Ugyldig gruppe eller antall uker. Uker må være 2 eller 4.")
                } else {
                    call.respond(perEnhetService.hentCachetVerdi(gruppe, checkNotNull(uker)))
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}

private fun krevK9(område: Områder) {
    if (område != Områder.K9) {
        throw IkkeImplementertException("Nøkkeltallet er ikke implementert for ${område.eksternId}")
    }
}
