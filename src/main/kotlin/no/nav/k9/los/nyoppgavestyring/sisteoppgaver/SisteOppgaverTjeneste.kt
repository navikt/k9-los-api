package no.nav.k9.los.nyoppgavestyring.sisteoppgaver

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Action
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Auditlogging
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.fnr
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.navn
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import kotlin.time.Duration.Companion.seconds

class SisteOppgaverTjeneste(
    private val sisteOppgaverRepository: SisteOppgaverRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val pepClient: IPepClient,
    private val pdlService: IPdlService,
    private val azureGraphService: IAzureGraphService,
    private val transactionalManager: TransactionalManager
) {
    suspend fun hentSisteOppgaver(): List<SisteOppgaverDto> {
        val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()

        val oppgaver = transactionalManager.transaction { tx ->
            val sisteOppgaveIds = sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandlerIdent)
            sisteOppgaveIds.map { eksternOppgaveId ->
                oppgaveRepository.hentNyesteOppgaveForEksternId(tx, eksternOppgaveId.område, eksternOppgaveId.eksternId)
            }
        }

        val innhentinger = oppgaver.map { oppgave ->
            withContext(Dispatchers.IO) {
                async(Span.current().asContextElement()) {
                    val harTilgang = pepClient.harTilgangTilOppgaveV3(oppgave, Action.read, Auditlogging.IKKE_LOGG)
                    val personPdl = oppgave.hentVerdi("aktorId")?.let { pdlService.person(it) }
                    Triple(harTilgang, personPdl, oppgave)
                }
            }
        }

        return withTimeout(5.seconds) { innhentinger.awaitAll() }
            .filter { (harTilgang) -> harTilgang }
            .map { (_, personPdl, oppgave) ->
                val navnOgFnr = personPdl?.person?.let { "${it.navn()} ${it.fnr()}" } ?: "Ukjent"
                val oppgavetypeTittel = when (oppgave.oppgavetype.eksternId) {
                    "k9sak" -> "K9"
                    "k9punsj" -> "Punsj"
                    "k9tilbake" -> "Tilbake"
                    "k9klage" -> "Klage"
                    else -> oppgave.oppgavetype.eksternId
                }
                SisteOppgaverDto(
                    oppgaveEksternId = oppgave.eksternId,
                    tittel = "$navnOgFnr ($oppgavetypeTittel)",
                    url = oppgave.getOppgaveBehandlingsurl(),
                )
            }
    }

    suspend fun lagreSisteOppgave(oppgaveNøkkelDto: OppgaveNøkkelDto) {
        val brukerIdent = azureGraphService.hentIdentTilInnloggetBruker()
        transactionalManager.transaction { tx ->
            sisteOppgaverRepository.lagreSisteOppgave(
                tx,
                brukerIdent,
                oppgaveNøkkelDto
            )
            sisteOppgaverRepository.ryddOppForBrukerIdent(tx, brukerIdent)
        }
    }
}