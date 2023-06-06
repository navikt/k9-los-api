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
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.ko.dto.OpprettOppgaveKoDto
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.tilCsv
import org.koin.java.KoinJavaComponent
import org.koin.ktor.ext.inject
import java.util.*

fun Route.OppgaveKoApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoRepository by inject<OppgaveKoRepository>()
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
    data class HentOppgaveKo(val id: String)

    get { hentOppgaveKo: HentOppgaveKo ->
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden);
            }

            call.respond(oppgaveKoRepository.hent(hentOppgaveKo.id.toLong()))
        }
    }

    @Location("/")
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