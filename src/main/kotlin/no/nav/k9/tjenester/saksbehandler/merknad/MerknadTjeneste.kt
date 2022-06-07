package no.nav.k9.tjenester.saksbehandler.merknad

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.lager.oppgave.v2.OppgaveV2
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.integrasjon.azuregraph.IAzureGraphService
import org.koin.ktor.ext.inject
import java.time.LocalDateTime

internal fun Route.MerknadApi() {
    val merknadTjeneste by inject<MerknadTjeneste>()

    route("/merknad/{eksternReferanse}") {
        get {
            val eksternReferanse = call.parameters["eksternReferanse"]
                ?: throw IllegalStateException("Mangler eksternReferanse i path")
            call.respond(
                merknadTjeneste.hentMerknad(eksternReferanse).map { MerknadResponse.avMerknad(it) }
            )
        }

        post {
            val eksternReferanse = call.parameters["eksternReferanse"]
                ?: throw IllegalStateException("Mangler eksternReferanse i path")
            val merknad = call.receive<MerknadEndret>()

            merknadTjeneste.lagreMerknad(eksternReferanse, merknad)
            call.respond(HttpStatusCode.OK)
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
    var id: Long?,
    val merknadKoder: List<String>,
    val fritekst: String?
) {
    fun nyMerknad(saksbehandlerIdent: String, aktiveOppgaver: List<OppgaveV2>): Merknad {
        return Merknad(
            id = id,
            oppgaveKoder = aktiveOppgaver.map { it.oppgaveKode },
            oppgaveIder = aktiveOppgaver.map { it.id!! },
            saksbehandler = saksbehandlerIdent,
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
    private val tm: TransactionalManager
) {

    fun hentMerknad(eksternReferanse: String): Set<Merknad> {
        return oppgaveRepositoryV2.hentBehandling(eksternReferanse)?.hentMerknader() ?: emptySet()
    }

    suspend fun lagreMerknad(eksternReferanse: String, merknad: MerknadEndret) {
        val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()
        tm.transaction {
            val behandling = oppgaveRepositoryV2.hentBehandling(eksternReferanse, it)
            behandling!!.lagreMerknad(merknad, saksbehandler = saksbehandlerIdent)
            oppgaveRepositoryV2.lagre(behandling, it)
        }

    }
}