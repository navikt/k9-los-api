package no.nav.k9.los.tjenester.avdelingsleder

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveApisTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OpphevReservasjonId
import org.koin.ktor.ext.inject
import java.util.*

internal fun Route.AvdelingslederApis() {
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val avdelingslederTjeneste by inject<AvdelingslederTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val oppgaveApisTjeneste by inject<OppgaveApisTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get("/oppgaver/antall-totalt") {
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveTjeneste.hentAntallOppgaverTotalt()) //TODO Må også telle oppgaver som finnes i V3 men ikke V1 (klageoppgaver)?
        }
    }

    //Gjelder bare gamle køer. For nye, bruk oppgaveKoApis/{id}/antall-oppgaver::GET
    get("/oppgaver/antall") {
        requestContextService.withRequestContext(call) {
            val uuid = call.parameters["id"]
            call.respond(
                oppgaveTjeneste.hentAntallOppgaver(
                    oppgavekøId = UUID.fromString(uuid),
                    taMedReserverte = true
                )
            )
        }
    }

    get("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.hentSaksbehandlere())
        }
    }

    post("/saksbehandlere/sok") {
        requestContextService.withRequestContext(call) {
            val epost = call.receive<EpostDto>()
            call.respond(avdelingslederTjeneste.søkSaksbehandler(epost))
        }
    }

    post("/saksbehandlere/slett") {
        requestContextService.withRequestContext(call) {
            val epost = call.receive<EpostDto>()
            call.respond(avdelingslederTjeneste.fjernSaksbehandler(epost.epost))
        }
    }

    get("/reservasjoner") {
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.hentAlleAktiveReservasjonerV3())
        }
    }

    post("/reservasjoner/opphev") {
        requestContextService.withRequestContext(call) {
            val nøkkel = call.receive<OppgaveNøkkelDto>()
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!
            call.respond(oppgaveApisTjeneste.annullerReservasjon(OpphevReservasjonId(oppgaveNøkkel = nøkkel, ""), innloggetBruker))
        }
    }
}
