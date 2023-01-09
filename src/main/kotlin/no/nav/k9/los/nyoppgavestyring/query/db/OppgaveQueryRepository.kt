package no.nav.k9.los.nyoppgavestyring.query.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.query.*
import javax.sql.DataSource

class OppgaveQueryRepository(val datasource: DataSource) {

    fun query(oppgaveQuery: OppgaveQuery): List<Long> {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> query(tx, oppgaveQuery) }
        }
    }

    fun query(tx: TransactionalSession, oppgaveQuery: OppgaveQuery): List<Long> {
        return query(tx, toSqlOppgaveQuery(oppgaveQuery))
    }


    private fun query(tx: TransactionalSession, oppgaveQuery: SqlOppgaveQuery): List<Long> {
        return tx.run(
            queryOf(
                oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map{row -> row.long("id")}.asList
        ) ?: throw IllegalStateException("Feil ved kjøring av oppgavequery")
    }

    fun toSqlOppgaveQuery(oppgaveQuery: OppgaveQuery): SqlOppgaveQuery {
        val query = SqlOppgaveQuery()
        val combineOperator = CombineOperator.AND;
        håndterFiltere(query, oppgaveQuery.filtere, combineOperator)

        return query
     }

    private fun håndterFiltere(
        query: SqlOppgaveQuery,
        filtere: List<Oppgavefilter>,
        combineOperator: CombineOperator
    ) {
        for (filter in filtere) {
            when (filter) {
                is FeltverdiOppgavefilter -> query.medFeltverdi(
                    combineOperator,
                    filter.område,
                    filter.kode,
                    FeltverdiOperator.valueOf(filter.operator),
                    filter.verdi
                )
                is CombineOppgavefilter -> {
                    val newCombineOperator = CombineOperator.valueOf(filter.combineOperator)
                    query.medBlokk(combineOperator, newCombineOperator.defaultValue) {
                        håndterFiltere(query, filter.filtere, newCombineOperator);
                    };
                }
                else -> throw IllegalStateException("Ukjent filter: " + filter::class.qualifiedName)
            }
        }
    }
}