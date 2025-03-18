package no.nav.k9.los.nyoppgavestyring.query.db

import kotliquery.Row
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator

interface OppgaveQuerySqlBuilder {
    val oppgavestatusFilter: List<Oppgavestatus>

    fun medFeltverdi(
        combineOperator: CombineOperator,
        feltområde: String?,
        feltkode: String,
        operator: FeltverdiOperator,
        feltverdi: Any?
    )
    fun medBlokk(combineOperator: CombineOperator, defaultTrue: Boolean, blokk: () -> Unit)
    fun medEnkelOrder(feltområde: String?, feltkode: String, økende: Boolean)
    fun utenReservasjoner()
    fun medPaging(limit: Long, offset: Long)
    fun medAntallSomResultat()

    fun getQuery(): String
    fun getParams(): Map<String, Any?>
    fun mapRowTilId(row: Row): OppgaveId
}
