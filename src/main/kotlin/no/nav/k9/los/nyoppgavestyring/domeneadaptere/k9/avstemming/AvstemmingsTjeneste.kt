package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.sak.Avstemmer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.sak.systemklient.K9SakAvstemmingsklient
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator

class AvstemmingsTjeneste(
    private val oppgaveQueryService: OppgaveQueryService,
    private val k9SakAvstemmingsklient: K9SakAvstemmingsklient,
    private val k9SakAvstemmer: Avstemmer
) {
    fun avstem(fagsystem: Fagsystem) : Avstemmingsrapport {
        return when (fagsystem) {
            Fagsystem.K9SAK -> {
                val åpneBehandlinger = k9SakAvstemmingsklient.hentÅpneBehandlinger()

                val query = OppgaveQuery(
                    filtere = listOf(
                        FeltverdiOppgavefilter(
                            "K9",
                            "oppgavetype",
                            operator = EksternFeltverdiOperator.EQUALS,
                            verdi = listOf("k9sak"),
                        ),
                        FeltverdiOppgavefilter(
                            "K9",
                            "oppgavestatus",
                            operator = EksternFeltverdiOperator.IN,
                            verdi = listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER),
                        )
                    )
                )
                val åpneOppgaver = oppgaveQueryService.queryForOppgave(QueryRequest(query))
                k9SakAvstemmer.regnUtDiff(åpneBehandlinger, åpneOppgaver)
            }
            Fagsystem.K9TILBAKE -> throw UnsupportedOperationException()
            Fagsystem.K9KLAGE -> throw UnsupportedOperationException()
            Fagsystem.PUNSJ -> throw UnsupportedOperationException()
        }
    }
}