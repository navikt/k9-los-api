package no.nav.k9.los.nyoppgavestyring.ko

import io.ktor.http.*
import io.ktor.server.application.call
import io.ktor.server.locations.*
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.KopierOppgaveKoDto
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.ko.dto.OpprettOppgaveKoDto
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveKøIdDto
import org.koin.java.KoinJavaComponent
import org.koin.ktor.ext.inject

fun Route.OppgaveKoApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoRepository by inject<OppgaveKoRepository>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
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
            call.respond(
                oppgaveKoRepository.kopier(
                    kopierOppgaveKoDto.kopierFraOppgaveId,
                    kopierOppgaveKoDto.tittel,
                    kopierOppgaveKoDto.taMedQuery,
                    kopierOppgaveKoDto.taMedSaksbehandlere
                )
            )
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
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        kotlin.coroutines.coroutineContext.idToken().getUsername()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    @Location("/{id}/oppgaver")
    data class OppgaveKoId(val id: String)
    get { oppgaveKoId: OppgaveKoId ->
        requestContextService.withRequestContext(call) {
            if (pepClient.harTilgangTilReservingAvOppgaver()) {
                call.respond(
                    oppgaveKoTjeneste.hentOppgaverFraKø(
                        oppgaveKoId.id.toLong(),
                        10
                    )
                ) //Finn et fornuftig antall?
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    @Location("/{id}/saksbehandlere")
    data class SaksbehandlereForOppgaveKo(val id: String)
    get { oppgaveKoId: SaksbehandlereForOppgaveKo ->
        requestContextService.withRequestContext(call) {
            call.respond(
                oppgaveKoTjeneste.hentSaksbehandlereForKo(oppgaveKoId.id.toLong())
            )
        }
    }

    @Location("/{id}/antall-oppgaver")
    data class AntallOppgaverIKo(val id: String)
    get { oppgaveKoId: AntallOppgaverIKo ->
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveKoTjeneste.hentAntallOppgaveForKø(oppgaveKoId.id.toLong()))
        }
    }

    @Location("/{id}/fa-oppgave")
    data class FaOppgaveFraKo(val id: String)
    post { oppgaveKoId: FaOppgaveFraKo ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveKøIdDto>()

            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            if (pepClient.harTilgangTilReservingAvOppgaver()) {
                val (reservertOppgave, reservasjonFraKø) = oppgaveKoTjeneste.taReservasjonFraKø(
                    innloggetBrukerId = innloggetBruker.id!!,
                    oppgaveKoId = params.oppgaveKøId.toLong(),
                    kotlin.coroutines.coroutineContext
                ) ?: Pair(null, null)

                if (reservasjonFraKø != null) {
                    call.respond(ReservasjonV3FraKøDto(reservasjonFraKø, reservertOppgave!!, innloggetBruker))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen oppgave i valgt kø")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden, "Innlogget bruker mangler tilgang til å reservere oppgaver")
            }
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