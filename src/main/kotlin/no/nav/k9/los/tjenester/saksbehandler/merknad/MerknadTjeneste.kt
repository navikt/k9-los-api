package no.nav.k9.los.tjenester.saksbehandler.merknad

import io.ktor.server.application.call
import io.ktor.http.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.k9.los.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveV2
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveKøOppdaterer
import org.koin.ktor.ext.inject
import java.time.LocalDateTime
import java.util.*

internal fun Route.MerknadApi() {
    val merknadTjeneste by inject<MerknadTjeneste>()

    route("/{eksternReferanse}/merknad") {
        get {
            val eksternReferanse = call.parameters["eksternReferanse"]
                ?: throw IllegalStateException("Mangler eksternReferanse i path")
            call.respond(
                merknadTjeneste.hentMerknad(eksternReferanse)?.let { MerknadResponse.avMerknad(it) }
                    ?: HttpStatusCode.NoContent
            )
        }

        post {
            val eksternReferanse = call.parameters["eksternReferanse"]
                ?: throw IllegalStateException("Mangler eksternReferanse i path")
            val merknad = call.receive<MerknadEndret>()

            call.respond(
                merknadTjeneste.lagreMerknad(eksternReferanse, merknad)?.let { MerknadResponse.avMerknad(it) }
                    ?: HttpStatusCode.NoContent
            )
        }
    }
}

data class MerknadResponse(
    val id: Long,
    val merknadKoder: List<String>,
    val oppgaveKoder: List<String>,
    val fritekst: String?,
    val opprettet: LocalDateTime,
    val sistEndret: LocalDateTime?
) {
    companion object {
        fun avMerknad(merknad: Merknad): MerknadResponse {
            return MerknadResponse(
                id = merknad.id!!,
                merknadKoder = merknad.merknadKoder,
                oppgaveKoder = merknad.oppgaveKoder,
                fritekst = merknad.fritekst,
                opprettet = merknad.opprettet,
                sistEndret = merknad.sistEndret
            )
        }
    }
}

data class MerknadEndret(
    val merknadKoder: List<String>,
    val fritekst: String?,
    val saksbehandlerIdent: String? = null
) {
    fun nyMerknad(saksbehandlerIdent: String?, aktiveOppgaver: List<OppgaveV2>): Merknad {
        return Merknad(
            oppgaveKoder = aktiveOppgaver.map { it.oppgaveKode },
            oppgaveIder = aktiveOppgaver.map { it.id!! },
            saksbehandler = saksbehandlerIdent ?: this.saksbehandlerIdent,
            opprettet = LocalDateTime.now(),
        ).also {
            it.oppdater(
                merknadKoder = merknadKoder,
                fritekst = fritekst
            )
        }
    }
}


class MerknadTjeneste(
    private val oppgaveRepositoryV2: OppgaveRepositoryV2,
    private val azureGraphService: IAzureGraphService,
    private val oppgaveKøOppdaterer: OppgaveKøOppdaterer,
    private val migreringstjeneste: BehandlingsmigreringTjeneste,
    private val k9SakTilLosAdapterTjeneste: K9SakTilLosAdapterTjeneste,
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val tm: TransactionalManager
) {

    fun hentMerknad(eksternReferanse: String): Merknad? {
        return oppgaveRepositoryV2.hentBehandling(eksternReferanse)?.merknad
    }

    suspend fun lagreMerknad(eksternReferanse: String, merknad: MerknadEndret): Merknad? {
        // Så lenge merknader går via k9-sak med systemtoken, må k9-sak legge ved denne identen
        val saksbehandlerIdent = try {
            azureGraphService.hentIdentTilInnloggetBruker()
        } catch (_: Exception) {
            null
        }
        val merknaderEtterLagring = tm.transaction { transaction ->
            val behandling = oppgaveRepositoryV2.hentBehandling(eksternReferanse, transaction)
                ?: migreringstjeneste.hentBehandlingFraTidligereProsessEvents(eksternReferanse)
                ?: throw IllegalStateException("Forsøker å lagre merknad på ukjent eksternReferanse $eksternReferanse")
            behandling.lagreMerknad(merknad, saksbehandlerIdent = saksbehandlerIdent)
            oppgaveRepositoryV2.lagre(behandling, transaction)

            val behandlingUUID = UUID.fromString(eksternReferanse)
            behandlingProsessEventK9Repository.settDirty(behandlingUUID, transaction)
            k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(behandlingUUID)

            behandling.merknad
        }
        oppgaveKøOppdaterer.oppdater(UUID.fromString(eksternReferanse))
        return merknaderEtterLagring?.takeIf { !it.slettet }
    }
}