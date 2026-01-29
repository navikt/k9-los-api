package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.SakAvstemmer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.systemklient.Avstemmingsklient
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave

class AvstemmingsTjeneste(
    private val oppgaveQueryService: OppgaveQueryService,
    private val k9SakAvstemmingsklient: Avstemmingsklient,
    private val k9KlageAvstemmingsklient: Avstemmingsklient,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(AvstemmingsTjeneste::class.java)

    suspend fun avstem(fagsystem: Fagsystem) : Avstemmingsrapport {
        log.info("Starter avstemming for fagsystem: $fagsystem")
        return when (fagsystem) {
            Fagsystem.K9SAK -> {
                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            null,
                            "oppgavetype",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("k9sak"),
                        ),
                        FeltverdiOppgavefilter(
                            null,
                            "oppgavestatus",
                            operator = EksternFeltverdiOperator.IN,
                            verdi = listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER, Oppgavestatus.UAVKLART),
                        )
                    )
                )
                var åpneOppgaver: List<Oppgave>
                var åpneBehandlinger: List<Behandlingstilstand>
                coroutineScope {
                    val åpneOppgaverDeferred = async { oppgaveQueryService.queryForOppgave(QueryRequest(query)) }
                    val åpneBehandlingerDeferred = async { k9SakAvstemmingsklient.hentÅpneBehandlinger() }
                    åpneOppgaver = åpneOppgaverDeferred.await()
                    åpneBehandlinger = åpneBehandlingerDeferred.await()
                }
                SakAvstemmer.regnUtDiff(Fagsystem.K9SAK, åpneBehandlinger, åpneOppgaver)
            }
            Fagsystem.K9KLAGE -> {
                log.info("Henter åpne behandlinger fra K9Klage")
                val åpneBehandlinger = k9KlageAvstemmingsklient.hentÅpneBehandlinger()
                log.info("Mottatt åpne behandlinger fra K9Klage")

                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            null,
                            "oppgavetype",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("k9klage"),
                        ),
                        FeltverdiOppgavefilter(
                            null,
                            "oppgavestatus",
                            operator = EksternFeltverdiOperator.IN,
                            verdi = listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER, Oppgavestatus.UAVKLART),
                        )
                    )
                )
                val åpneOppgaver = oppgaveQueryService.queryForOppgave(QueryRequest(query))
                SakAvstemmer.regnUtDiff(Fagsystem.K9KLAGE, åpneBehandlinger, åpneOppgaver)
            }
            Fagsystem.K9TILBAKE -> throw UnsupportedOperationException()
            Fagsystem.PUNSJ -> throw UnsupportedOperationException()
        }
    }
}