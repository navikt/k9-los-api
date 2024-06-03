package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.db.*
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import java.time.LocalDateTime

object OppgaveQueryToSqlMapper {
    fun toSqlOppgaveQuery(oppgaveQuery: OppgaveQuery, felter: Map<OmrådeOgKode, OppgavefeltMedMer>, now: LocalDateTime): SqlOppgaveQuery {
        val query = SqlOppgaveQuery(felter, now)
        val combineOperator = CombineOperator.AND

        håndterFiltere(query, oppgaveQuery.filtere, combineOperator)
        håndterOrder(query, oppgaveQuery.order)
        query.medLimit(oppgaveQuery.limit)

        return query
    }

    fun toSqlOppgaveQueryForAntall(
        oppgaveQuery: OppgaveQuery,
        felter: Map<OmrådeOgKode, OppgavefeltMedMer>,
        now: LocalDateTime
    ): SqlOppgaveQuery {
        val query = SqlOppgaveQuery(felter, now)
        val combineOperator = CombineOperator.AND
        håndterFiltere(query, oppgaveQuery.filtere, combineOperator)
        query.medAntallSomResultat()

        return query
    }

    private fun håndterFiltere(
        query: SqlOppgaveQuery,
        filtere: List<Oppgavefilter>,
        combineOperator: CombineOperator
    ) {
        for (filter in OppgavefilterUtvider.utvid(filtere)) {
            when (filter) {
                is FeltverdiOppgavefilter -> query.medFeltverdi(
                    combineOperator,
                    filter.område,
                    filter.kode,
                    FeltverdiOperator.valueOf(filter.operator),
                    filter.verdi.first()
                )

                is CombineOppgavefilter -> {
                    val newCombineOperator = CombineOperator.valueOf(filter.combineOperator)
                    query.medBlokk(combineOperator, newCombineOperator.defaultValue) {
                        håndterFiltere(query, filter.filtere, newCombineOperator)
                    }
                }

                else -> throw IllegalStateException("Ukjent filter: " + filter::class.qualifiedName)
            }
        }
    }

    private fun håndterOrder(query: SqlOppgaveQuery, orderBys: List<OrderFelt>) {
        for (orderBy in orderBys) {
            when (orderBy) {
                is EnkelOrderFelt -> query.medEnkelOrder(orderBy.område, orderBy.kode, orderBy.økende)
                else -> throw IllegalStateException("Ukjent OrderFelt: " + orderBy::class.qualifiedName)
            }
        }
    }
}