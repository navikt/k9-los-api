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
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

class SisteOppgaverTjeneste(
    private val sisteOppgaverRepository: SisteOppgaverRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val pepClient: IPepClient,
    private val pdlService: IPdlService,
    private val azureGraphService: IAzureGraphService,
    private val transactionalManager: TransactionalManager
) {
    private val log = LoggerFactory.getLogger(SisteOppgaverTjeneste::class.java)

    suspend fun hentSisteOppgaver(): List<SisteOppgaverDto> {
        return try {
            val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()

            val oppgaver =
                transactionalManager.transaction { tx ->
                    val sisteOppgaveIds = sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandlerIdent)
                    sisteOppgaveIds.map { eksternOppgaveId ->
                        oppgaveRepository.hentNyesteOppgaveForEksternId(
                            tx,
                            eksternOppgaveId.område,
                            eksternOppgaveId.eksternId
                        )
                    }
                }

            val grupperForSaksbehandler = azureGraphService.hentGrupperForInnloggetSaksbehandler()

            val innhentinger = try {
                withContext(Dispatchers.IO + Span.current().asContextElement()) {
                    oppgaver.map { oppgave ->
                        async {
                            try {
                                val harTilgang = pepClient.harTilgangTilOppgaveV3(
                                    oppgave,
                                    Action.read,
                                    Auditlogging.IKKE_LOGG,
                                    grupperForSaksbehandler
                                )
                                val personPdl = oppgave.hentVerdi("aktorId")?.let {
                                    pdlService.person(it)
                                }
                                Triple(harTilgang, personPdl, oppgave)
                            } catch (e: Exception) {
                                log.info("Feil ved pep- og pdl-kall av oppgave ${oppgave.eksternId}", e)
                                Triple(false, null, oppgave)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("Feil ved parallell innhenting av tilgangs- og persondata", e)
                throw e
            }

            try {
                withTimeout(5.seconds) { innhentinger.awaitAll() }
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
            } catch (e: TimeoutCancellationException) {
                log.warn("Timeout ved henting av siste oppgaver - operasjonen tok lengre enn 5 sekunder", e)
                throw e
            }
        } catch (e: Exception) {
            log.warn("Uventet feil ved henting av siste oppgaver", e)
            throw e
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