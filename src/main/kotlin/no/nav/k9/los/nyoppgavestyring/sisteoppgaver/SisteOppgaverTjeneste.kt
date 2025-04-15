package no.nav.k9.los.nyoppgavestyring.sisteoppgaver

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

class SisteOppgaverTjeneste(
    private val sisteOppgaverRepository: SisteOppgaverRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val pepClient: IPepClient,
    private val pdlService: IPdlService,
    private val azureGraphService: IAzureGraphService,
    private val transactionalManager: TransactionalManager
) {
    suspend fun hentSisteOppgaver(
        scope: CoroutineScope,
    ): List<SisteOppgaverDto> {
        val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()
        return transactionalManager.transaction { tx ->
            val oppgaver = sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandlerIdent)
                .map { oppgaveRepository.hentNyesteOppgaveForEksternId(tx, it.område, it.eksternId) }

            val innhentinger = oppgaver.map { oppgave ->
                scope.async {
                    val harTilgang = pepClient.harTilgangTilOppgaveV3(oppgave, Action.read, Auditlogging.IKKE_LOGG)
                    val personPdl = oppgave.hentVerdi("aktorId")?.let { pdlService.person(it) }
                    Triple(harTilgang, personPdl, oppgave)
                }
            }

            runBlocking(Dispatchers.IO) { innhentinger.awaitAll() }
                .filter { (harTilgang) -> harTilgang }
                .map { (_, personPdl, oppgave) ->
                    val navOgFnr = personPdl?.person?.let { "${it.navn()} ${it.fnr()}" } ?: "Ukjent"
                    SisteOppgaverDto(
                        oppgaveEksternId = oppgave.eksternId,
                        tittel = "$navOgFnr (${oppgave.oppgavetype.eksternId})",
                        url = oppgave.getOppgaveBehandlingsurl(),
                    )
                }
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