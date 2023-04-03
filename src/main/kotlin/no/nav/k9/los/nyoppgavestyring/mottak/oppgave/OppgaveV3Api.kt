package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.K9SakModell
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosAdapterTjeneste
import org.koin.ktor.ext.inject
import java.time.LocalDateTime
import java.util.*

internal fun Route.OppgaveV3Api() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveV3Tjeneste by inject<OppgaveV3Tjeneste>()
    val transactionalManager by inject<TransactionalManager>()
    val behandlingProsessEventK9Repository by inject<BehandlingProsessEventK9Repository>()
    val config by inject<Configuration>()

    put {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val oppgaveDto = call.receive<OppgaveDto>()

                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)
                }

                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    get("/{område}/{eksternId}/{eksternVersjon}") {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                call.respond(
                    transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentOppgaveversjon(
                            område = call.parameters["område"]!!,
                            eksternId = call.parameters["eksternId"]!!,
                            eksternVersjon = call.parameters["eksternVersjon"]!!,
                            tx = tx
                        )
                    }
                )
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    post ( "/{område}/{eksternId}/{eksternVersjon}" ) {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val oppgaveDto = call.receive<OppgaveDto>()
                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.oppdaterEkstisterendeOppgaveversjon(oppgaveDto, tx)
                }
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    // TODO fjernes før prodsetting, bare for test
    post("/lagbehandlinger") {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val k9SakModell = jacksonObjectMapper().registerModule(JavaTimeModule()).readValue(
                    K9SakTilLosAdapterTjeneste::class.java.getResource("/adapterdefinisjoner/test.json")!!
                        .readText(),
                    K9SakModell::class.java
                )
                var behandlinger = 1
                while (behandlinger <= 500) {
                    val uuid = UUID.randomUUID()
                    val genererteEventer = k9SakModell.eventer.map { behandlingProsessEventDto ->
                        behandlingProsessEventDto.copy(
                            eksternId = uuid,
                            eventTid = LocalDateTime.now()
                        )
                    }.toMutableList()

                    behandlingProsessEventK9Repository.lagreNy(uuid, K9SakModell(eventer = genererteEventer))
                    ++behandlinger
                    println("Opprettet behandling")
                }
            }
            call.respond("OK")
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }
}