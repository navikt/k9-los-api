package no.nav.k9.los.nyoppgavestyring.query.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Verdiforklaring
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
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
                      fd.tolkes_som as tolkes_som,
                      fd.kodeverk_id as kodeverk_id
                    FROM Feltdefinisjon fd INNER JOIN Omrade fo ON (
                      fo.id = fd.omrade_id
                    )
                """.trimIndent()
            ).map { row ->
                Oppgavefelt(
                    område = row.string("omrade"),
                    kode = row.string("kode"),
                    visningsnavn = midlertidigFiksVisningsnavn(row.string("visningsnavn")),
                    tolkes_som = row.string("tolkes_som"),
                    verdier = hentVerdiforklaringer(row.long("kodeverk"), tx)
                )
            }.asList
        ) ?: throw IllegalStateException("Feil ved kjøring av hentAlleFelter")

        val standardfelter = listOf(
            Oppgavefelt(
                null,
                "oppgavestatus",
                "Oppgavestatus",
                "String",
                Oppgavestatus.values().map { oppgavestatus ->
                    Verdiforklaring(
                        verdi = oppgavestatus.kode,
                        visningsnavn = oppgavestatus.visningsnavn
                    )
                }),
            Oppgavefelt(
                null,
                "kildeområde",
                "Kildeområde",
                "String",
                emptyList()
                ),
            Oppgavefelt(null, "oppgavetype", "Oppgavetype", "String", emptyList()),
            Oppgavefelt(null, "oppgaveområde", "Oppgaveområde", "String", emptyList()
            )
        )

        return (felterFraDatabase + standardfelter).sortedBy { it.visningsnavn };
    }

    private fun hentVerdiforklaringer(kodeverk_id: Long, tx: TransactionalSession): List<Verdiforklaring> {
        return tx.run(
            queryOf(
                """
                    select 
                        kv.verdi as verdi,
                        kv.visningsnavn as visningsnavn
                    from kodeverk_verdi kv where kodeverk_id = :kodeverk_id
                """.trimIndent(),
                mapOf(
                    "kodeverk_id" to kodeverk_id
                )
            ).map { row ->
                Verdiforklaring(
                    verdi = row.string("verdi"),
                    visningsnavn = row.string("visningsnavn")
                )
            }.asList
        )
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
            ).map { row -> row.long("id") }.asList
        ) ?: throw IllegalStateException("Feil ved kjøring av oppgavequery")
    }

    fun toSqlOppgaveQuery(oppgaveQuery: OppgaveQuery): SqlOppgaveQuery {
        val query = SqlOppgaveQuery()
        val combineOperator = CombineOperator.AND;
        håndterFiltere(query, oppgaveQuery.filtere, combineOperator)
        håndterOrder(query, oppgaveQuery.order)
        query.medLimit(oppgaveQuery.limit)

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

    private fun håndterOrder(query: SqlOppgaveQuery, orderBys: List<OrderFelt>) {
        for (orderBy in orderBys) {
            when (orderBy) {
                is EnkelOrderFelt -> query.medEnkelOrder(orderBy.område, orderBy.kode, orderBy.økende)
                else -> throw IllegalStateException("Ukjent OrderFelt: " + orderBy::class.qualifiedName)
            }
        }
    }
}