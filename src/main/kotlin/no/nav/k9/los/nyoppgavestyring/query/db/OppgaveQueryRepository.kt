package no.nav.k9.los.nyoppgavestyring.query.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import javax.sql.DataSource

class OppgaveQueryRepository(val datasource: DataSource) {

    fun hentAlleFelter(): Oppgavefelter {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> Oppgavefelter(hentAlleFelter(tx)) }
        }
    }

    private fun midlertidigFiksVisningsnavn(kode: String): String {
        val s = kode.replace("([A-ZÆØÅ])".toRegex(), " $1").lowercase();
        return s.substring(0, 1).uppercase() + s.substring(1)
    }

    private fun hentAlleFelter(tx: TransactionalSession): List<Oppgavefelt> {
        val felterFraDatabase = tx.run(
            queryOf(
                """
                    SELECT DISTINCT fo.ekstern_id as omrade,
                      fd.ekstern_id as kode,
                      fd.ekstern_id as visningsnavn,
                      fd.tolkes_som as tolkes_som
                    FROM Feltdefinisjon fd INNER JOIN Omrade fo ON (
                      fo.id = fd.omrade_id
                    )
                """.trimIndent()
            ).map{row -> Oppgavefelt(
                row.string("omrade"),
                row.string("kode"),
                midlertidigFiksVisningsnavn(row.string("visningsnavn")),
                row.string("tolkes_som")
            ) }.asList
        ) ?: throw IllegalStateException("Feil ved kjøring av hentAlleFelter")

        val standardfelter = listOf(
            Oppgavefelt(null, "oppgavestatus", "Oppgavestatus", "String"),
            Oppgavefelt(null, "kildeområde", "Kildeområde", "String"),
            Oppgavefelt(null, "oppgavetype", "Oppgavetype", "String"),
            Oppgavefelt(null, "oppgaveområde", "Oppgaveområde", "String")
        )

        return (felterFraDatabase + standardfelter).sortedBy { it.visningsnavn };
    }

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