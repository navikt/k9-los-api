package no.nav.k9.los.nøkkeltall

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.kontekst.medBrukerkontekst
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nøkkeltall.avdelingsleder.dagenstall.DagensTallService
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetGruppe
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.nøkkeltall.avdelingsleder.status.StatusService
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.område
import org.koin.ktor.ext.inject

fun Route.NøkkeltallV3ApisNy() {
    val statusFordelingService by inject<StatusFordelingService>()
    val statusService by inject<StatusService>()
    val dagensTallService by inject<DagensTallService>()
    val perEnhetService by inject<FerdigstiltePerEnhetService>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get("status", {
        description = "Hent status for oppgavekøer."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val område = kontekst.område
                call.respond(statusService.hentStatus(pepClient.harTilgangTilKode6(kontekst)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("statusfordeling", {
        description = "Hent fordeling av oppgaver på status."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val område = kontekst.område
                val kode6 = pepClient.harTilgangTilKode6(kontekst)
                call.respond(statusFordelingService.hentVerdi(kode6))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("dagens-tall", {
        description = "Hent dagens tall for nye og ferdigstilte oppgaver."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val område = kontekst.område
                call.respond(dagensTallService.hentCachetVerdi())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("ferdigstilte-per-enhet", {
        description = "Hent antall ferdigstilte oppgaver per enhet, eventuelt filtrert på ytelsesgruppe og antall uker."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            queryParameter<FerdigstiltePerEnhetGruppe>("gruppe") {
                description = "Ytelsesgruppe å filtrere på. Dersom utelatt returneres tall for alle ytelser."
                required = false
                example("ALLE") { value = FerdigstiltePerEnhetGruppe.ALLE }
            }
            queryParameter<Int>("uker") {
                description = "Antall uker tilbake i tid det skal hentes tall for. Default er 2 uker."
                required = false
                example("2") { value = 2 }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val område = kontekst.område
                val gruppe = call.parameters["gruppe"]?.let { FerdigstiltePerEnhetGruppe.valueOf(it) }
                    ?: FerdigstiltePerEnhetGruppe.ALLE
                val uker = call.parameters["uker"]?.toInt() ?: 2
                call.respond(perEnhetService.hentCachetVerdi(gruppe, uker))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
