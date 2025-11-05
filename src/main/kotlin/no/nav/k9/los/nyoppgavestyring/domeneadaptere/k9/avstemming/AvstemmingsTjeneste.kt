package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.Avstemmer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.systemklient.Avstemmingsklient
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator

class AvstemmingsTjeneste(
    private val oppgaveQueryService: OppgaveQueryService,
    private val k9SakAvstemmingsklient: Avstemmingsklient,
    private val k9KlageAvstemmingsklient: Avstemmingsklient,
    private val SakAvstemmer: Avstemmer
) {
    fun avstem(fagsystem: Fagsystem) : Avstemmingsrapport {
        return when (fagsystem) {
            Fagsystem.K9SAK -> {
                val åpneBehandlinger = k9SakAvstemmingsklient.hentÅpneBehandlinger()

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
                            verdi = listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER),
                        )
                    )
                )
                val åpneOppgaver = oppgaveQueryService.queryForOppgave(QueryRequest(query))
                SakAvstemmer.regnUtDiff(åpneBehandlinger, åpneOppgaver)
            }
            Fagsystem.K9KLAGE -> {
                val åpneBehandlinger = k9KlageAvstemmingsklient.hentÅpneBehandlinger()

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
                            verdi = listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER),
                        )
                    )
                )
                val åpneOppgaver = oppgaveQueryService.queryForOppgave(QueryRequest(query))
                SakAvstemmer.regnUtDiff(åpneBehandlinger, åpneOppgaver)
            }
            Fagsystem.K9TILBAKE -> throw UnsupportedOperationException()
            Fagsystem.PUNSJ -> throw UnsupportedOperationException()
        }
    }
}