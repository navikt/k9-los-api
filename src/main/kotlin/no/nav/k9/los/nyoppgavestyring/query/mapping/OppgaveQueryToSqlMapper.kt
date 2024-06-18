package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.db.*
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import java.time.LocalDateTime

object OppgaveQueryToSqlMapper {
    fun toSqlOppgaveQuery(request: QueryRequest, felter: Map<OmrådeOgKode, OppgavefeltMedMer>, now: LocalDateTime): OppgaveQuerySqlBuilder {
        val query = OppgaveQuerySqlBuilder(felter, traverserFiltereOgFinnOppgavestatusfilter(request), now)
        val combineOperator = CombineOperator.AND

        håndterFiltere(query, request.oppgaveQuery.filtere, combineOperator)
        håndterOrder(query, request.oppgaveQuery.order)
        if (request.fjernReserverte) {
            query.utenReservasjoner()
        }
        request.avgrensning?.let { query.medPaging(it.limit, it.offset) }

        return query
    }

    fun toSqlOppgaveQueryForAntall(
        request: QueryRequest,
        felter: Map<OmrådeOgKode, OppgavefeltMedMer>,
        now: LocalDateTime
    ): OppgaveQuerySqlBuilder {
        val query = OppgaveQuerySqlBuilder(felter, traverserFiltereOgFinnOppgavestatusfilter(request), now)
        val combineOperator = CombineOperator.AND
        håndterFiltere(query, request.oppgaveQuery.filtere, combineOperator)
        if (request.fjernReserverte) { query.utenReservasjoner() }
        request.avgrensning?.let { query.medPaging(it.limit, it.offset) }
        query.medAntallSomResultat()

        return query
    }

    private fun traverserFiltereOgFinnOppgavestatusfilter(queryRequest: QueryRequest): List<Oppgavestatus> {
        val statuser = mutableSetOf<Oppgavestatus>()
        rekursivtSøk(queryRequest.oppgaveQuery.filtere, statuser)

        //dette parameteret brukes av index på oppgavefeltverdi. Spørringer som ser på lukkede oppgaver er ikke indekserte, og vil være trege
        //Dersom spørringen filterer på oppgavestatus, så matcher vi det.
        //Hvis spørringen ikke filterer på oppgavestatus, må vi tillate alle verdier, for at spørringen skal fungere riktig
        return if (statuser.isNotEmpty()) {
            statuser.toList()
        } else {
            Oppgavestatus.entries
        }
    }

    private fun rekursivtSøk(
        filtere: List<Oppgavefilter>,
        statuser: MutableSet<Oppgavestatus>
    ) {
        for (filter in filtere) {
            if (filter is FeltverdiOppgavefilter && filter.kode == "oppgavestatus") {
                statuser.addAll(filter.verdi.map { verdi -> Oppgavestatus.fraKode(verdi.toString()) })
            } else if (filter is CombineOppgavefilter) {
                rekursivtSøk(filter.filtere, statuser)
            }
        }
    }

    private fun håndterFiltere(
        query: OppgaveQuerySqlBuilder,
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

    private fun håndterOrder(query: OppgaveQuerySqlBuilder, orderBys: List<OrderFelt>) {
        for (orderBy in orderBys) {
            when (orderBy) {
                is EnkelOrderFelt -> query.medEnkelOrder(orderBy.område, orderBy.kode, orderBy.økende)
                else -> throw IllegalStateException("Ukjent OrderFelt: " + orderBy::class.qualifiedName)
            }
        }
    }
}