package no.nav.k9.los.nyoppgavestyring.query.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Kodeverkreferanse
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelter
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Verdiforklaring
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import javax.sql.DataSource

class OppgaveQueryRepository(
    val datasource: DataSource,
    val feltdefinisjonRepository: FeltdefinisjonRepository
) {

    fun hentAlleFelter(): Oppgavefelter {
        return using(sessionOf(datasource)) { it ->
            it.transaction { tx -> Oppgavefelter(hentAlleFelter(tx)) }
        }
    }

    private fun hentAlleFelter(tx: TransactionalSession, medKodeverk: Boolean = true): List<Oppgavefelt> {
        val felterFraDatabase = tx.run(
            queryOf(
                """
                    SELECT DISTINCT fo.ekstern_id as omrade,
                      fd.ekstern_id as kode,
                      fd.visningsnavn as visningsnavn,
                      fd.tolkes_som as tolkes_som,
                      fd.kokriterie as kokriterie,
                      fd.kodeverkreferanse as kodeverkreferanse
                    FROM Feltdefinisjon fd INNER JOIN Omrade fo ON (
                      fo.id = fd.omrade_id
                    )
                    WHERE fd.vis_til_bruker
                """.trimIndent()
            ).map { row ->
                val kodeverk = if (medKodeverk) {
                    row.stringOrNull("kodeverkreferanse")?.let {
                        feltdefinisjonRepository.hentKodeverk(Kodeverkreferanse(it), tx)
                    }
                } else {
                    null
                }
                Oppgavefelt(
                    område = row.string("omrade"),
                    kode = row.string("kode"),
                    visningsnavn = row.string("visningsnavn"),
                    tolkes_som = row.string("tolkes_som"),
                    kokriterie = row.boolean("kokriterie"),
                    verdiforklaringerErUttømmende = kodeverk?.uttømmende ?: false,
                    verdiforklaringer = kodeverk?.let { kodeverk ->
                        kodeverk.verdier.map { kodeverkverdi ->
                            Verdiforklaring(
                                verdi = kodeverkverdi.verdi,
                                visningsnavn = kodeverkverdi.visningsnavn
                            )
                        }
                    }
                )
            }.asList
        ) ?: throw IllegalStateException("Feil ved kjøring av hentAlleFelter")

        val standardfelter = listOf(
            Oppgavefelt(
                null,
                "oppgavestatus",
                "Oppgavestatus",
                "String",
                kokriterie = true,
                verdiforklaringerErUttømmende = true,
                Oppgavestatus.values().map { oppgavestatus ->
                    Verdiforklaring(
                        verdi = oppgavestatus.kode,
                        visningsnavn = oppgavestatus.visningsnavn
                    )
                }),
            Oppgavefelt(null, "kildeområde", "Kildeområde", "String", false, false, emptyList()),
            Oppgavefelt(null, "oppgavetype", "Oppgavetype", "String", true, false, emptyList()),
            Oppgavefelt(null, "oppgaveområde", "Oppgaveområde", "String", false, false, emptyList())
        )

        return (felterFraDatabase + standardfelter).sortedBy { it.visningsnavn };
    }

    fun query(oppgaveQuery: OppgaveQuery): List<Long> {
        return using(sessionOf(datasource)) {
            it.transaction { tx -> query(tx, oppgaveQuery) }
        }
    }

    fun query(tx: TransactionalSession, oppgaveQuery: OppgaveQuery): List<Long> {
        val oppgavefelterKodeOgType = hentAlleFelter(tx, medKodeverk = false)
            .associate { felt -> felt.kode to Datatype.fraKode(felt.tolkes_som) }

        return query(tx, toSqlOppgaveQuery(oppgaveQuery, oppgavefelterKodeOgType))
    }

    fun queryForAntall(tx: TransactionalSession, oppgaveQuery: OppgaveQuery): Long {
        val oppgavefelterKodeOgType = hentAlleFelter(tx, medKodeverk = false)
            .associate { felt -> felt.kode to Datatype.fraKode(felt.tolkes_som) }


        return queryForAntall(tx, toSqlOppgaveQueryForAntall(oppgaveQuery, oppgavefelterKodeOgType))

    }

    private fun queryForAntall(tx: TransactionalSession, oppgaveQuery: SqlOppgaveQuery): Long {
        return tx.run(
            queryOf(
                oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map { row -> row.long("antall") }.asSingle
        )!!
    }

    private fun query(tx: TransactionalSession, oppgaveQuery: SqlOppgaveQuery): List<Long> {
        return tx.run(
            queryOf(
                oppgaveQuery.getQuery(),
                oppgaveQuery.getParams()
            ).map { row -> row.long("id") }.asList
        )
    }

    fun toSqlOppgaveQuery(oppgaveQuery: OppgaveQuery, oppgavefelterKodeOgType: Map<String, Datatype>): SqlOppgaveQuery {
        val query = SqlOppgaveQuery(oppgavefelterKodeOgType)
        val combineOperator = CombineOperator.AND
        håndterFiltere(query, oppgaveQuery.filtere, combineOperator)
        håndterOrder(query, oppgaveQuery.order)
        query.medLimit(oppgaveQuery.limit)

        return query
    }

    fun toSqlOppgaveQueryForAntall(
        oppgaveQuery: OppgaveQuery,
        oppgavefelterKodeOgType: Map<String, Datatype>
    ): SqlOppgaveQuery {
        val query = SqlOppgaveQuery(oppgavefelterKodeOgType)
        val combineOperator = CombineOperator.AND
        håndterFiltere(query, oppgaveQuery.filtere, combineOperator)
        håndterOrder(query, oppgaveQuery.order)
        query.medLimit(oppgaveQuery.limit)
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