package no.nav.k9.los.nyoppgavestyring.ko

import io.ktor.http.*
import io.ktor.server.application.call
import io.ktor.server.locations.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.KopierOppgaveKoDto
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.ko.dto.OpprettOppgaveKoDto
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveKøIdDto
import no.nav.k9.los.tjenester.saksbehandler.oppgave.ReservasjonV3FraKøDto
import org.koin.java.KoinJavaComponent
import org.koin.ktor.ext.inject
import java.util.*

fun Route.OppgaveKoApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoRepository by inject<OppgaveKoRepository>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val pepClient by KoinJavaComponent.inject<IPepClient>(IPepClient::class.java)

    @Location("/")
    class hentOppgaveKoer

    get { _: hentOppgaveKoer ->
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden);
            }

            call.respond(oppgaveKoRepository.hentListe())
        }
    }

    @Location("/kopier")
    class kopierOppgaveKo

    post { _: kopierOppgaveKo ->
        val kopierOppgaveKoDto = call.receive<KopierOppgaveKoDto>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden);
            }
            call.respond(oppgaveKoRepository.kopier(
                kopierOppgaveKoDto.kopierFraOppgaveId,
                kopierOppgaveKoDto.tittel,
                kopierOppgaveKoDto.taMedQuery,
                kopierOppgaveKoDto.taMedSaksbehandlere
            ))
        }
    }

    @Location("/opprett")
    class opprettOppgaveKo

    post { _: opprettOppgaveKo ->
        val opprettOppgaveKoDto = call.receive<OpprettOppgaveKoDto>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden);
            }
            call.respond(oppgaveKoRepository.leggTil(opprettOppgaveKoDto.tittel))
        }
    }

    @Location("/{id}")
    data class OppgaveKoParams(val id: String)

    get { oppgaveKoParams: OppgaveKoParams ->
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden);
            }

            call.respond(oppgaveKoRepository.hent(oppgaveKoParams.id.toLong()))
        }
    }

    delete { oppgaveKoParams: OppgaveKoParams ->
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden);
            }

            call.respond(oppgaveKoRepository.slett(oppgaveKoParams.id.toLong()))
        }
    }

    @Location("/saksbehandlerskoer")
    class SaksbehandlersKoer

    get { _: SaksbehandlersKoer ->
        call.respond(HttpStatusCode.OK)
    }


    @Location("/{id}/oppgaver")
    data class OppgaveKoId(val id: String)
    get { oppgaveKoId: OppgaveKoId ->
        requestContextService.withRequestContext(call) {
            if (pepClient.harTilgangTilReservingAvOppgaver()) { //TODO: Hvilke felter vil vi eksponere her?
                oppgaveKoTjeneste.hentOppgaverFraKø(oppgaveKoId.id.toLong(), 10) //Finn et fornuftig antall?
            }
        }
    }

    @Location("/{id}/saksbehandlere")
    data class SaksbehandlereForOppgaveKo(val id: String)
    get {oppgaveKoId: SaksbehandlereForOppgaveKo ->
        requestContextService.withRequestContext(call) {
            call.respond(
                oppgaveKoTjeneste.hentSaksbehandlereForKo(oppgaveKoId.id.toLong())
            )
        }
    }

    @Location("")
    class endreOppgaveKo

    post { _: endreOppgaveKo ->
        val oppgaveKo = call.receive<OppgaveKo>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden);
            }
            call.respond(oppgaveKoRepository.endre(oppgaveKo))
        }
    }
}