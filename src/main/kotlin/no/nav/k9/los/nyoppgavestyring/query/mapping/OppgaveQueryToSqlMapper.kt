package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.db.*
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import java.time.LocalDateTime

object OppgaveQueryToSqlMapper {
    private fun utledSqlBuilder(
        felter: Map<OmrådeOgKode, OppgavefeltMedMer>,
        request: QueryRequest,
        now: LocalDateTime
    ): OppgaveQuerySqlBuilder {
        val oppgavestatusFilter = traverserFiltereOgFinnOppgavestatusfilter(request)
        return when {
            oppgavestatusFilter.isEmpty() || oppgavestatusFilter.contains(Oppgavestatus.LUKKET) -> PartisjonertOppgaveQuerySqlBuilder(felter, oppgavestatusFilter, now)
            else -> AktivOppgaveQuerySqlBuilder(felter, oppgavestatusFilter, now)
        }
    }

    fun toSqlOppgaveQuery(request: QueryRequest, felter: Map<OmrådeOgKode, OppgavefeltMedMer>, now: LocalDateTime): OppgaveQuerySqlBuilder {
        val query = utledSqlBuilder(felter, request, now)
        val combineOperator = CombineOperator.AND

        håndterFiltere(query, felter, OppgavefilterRens.rens(felter, request.oppgaveQuery.filtere), combineOperator)
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
        val queryBuilder = utledSqlBuilder(felter, request, now)
        val combineOperator = CombineOperator.AND
        håndterFiltere(queryBuilder, felter, OppgavefilterRens.rens(felter, request.oppgaveQuery.filtere), combineOperator)
        if (request.fjernReserverte) { queryBuilder.utenReservasjoner() }
        request.avgrensning?.let { queryBuilder.medPaging(it.limit, it.offset) }
        queryBuilder.medAntallSomResultat()
        return queryBuilder
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
        queryBuilder: OppgaveQuerySqlBuilder,
        felter:  Map<OmrådeOgKode, OppgavefeltMedMer>,
        filtere: List<Oppgavefilter>,
        combineOperator: CombineOperator
    ) {
        for (filter in filtere) {
            when (filter) {
                is FeltverdiOppgavefilter -> queryBuilder.medFeltverdi(
                    combineOperator,
                    filter.område,
                    filter.kode,
                    FeltverdiOperator.valueOf(filter.operator),
                    filter.verdi.first()
                )

                is CombineOppgavefilter -> {
                    val newCombineOperator = CombineOperator.valueOf(filter.combineOperator)
                    queryBuilder.medBlokk(combineOperator, newCombineOperator.defaultValue) {
                        håndterFiltere(queryBuilder, felter, filter.filtere, newCombineOperator)
                    }
                }
            }
        }
    }

    private fun håndterOrder(query: OppgaveQuerySqlBuilder, orderBys: List<OrderFelt>) {
        for (orderBy in orderBys) {
            when (orderBy) {
                is EnkelOrderFelt -> query.medEnkelOrder(orderBy.område, orderBy.kode, orderBy.økende)
            }
        }
    }
}