package no.nav.k9.los.nyoppgavestyring.query.db

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object OppgavefilterDatoTypeUtvider {

    fun utvid(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere.utvidListe()
    }

    private fun List<Oppgavefilter>.utvidListe(): List<Oppgavefilter> {
        return flatMap { filter ->
            when (filter) {
                is FeltverdiOppgavefilter -> filter.map()
                is CombineOppgavefilter -> listOf(
                    CombineOppgavefilter(
                        combineOperator = filter.combineOperator,
                        filter.filtere.utvidListe()
                    )
                )
                else -> throw IllegalStateException("Ukjent filter: " + filter::class.qualifiedName)
            }
        }
    }

    fun FeltverdiOppgavefilter.map(): List<Oppgavefilter> {
        val nedreØvreGrensebetingelse = when (operator) {
            FeltverdiOperator.EQUALS.name -> (FeltverdiOperator.GREATER_THAN_OR_EQUALS to FeltverdiOperator.LESS_THAN_OR_EQUALS)
            FeltverdiOperator.GREATER_THAN_OR_EQUALS.name -> (FeltverdiOperator.GREATER_THAN_OR_EQUALS to null)
            FeltverdiOperator.GREATER_THAN.name -> (null to FeltverdiOperator.GREATER_THAN)
            FeltverdiOperator.LESS_THAN_OR_EQUALS.name -> (null to FeltverdiOperator.LESS_THAN_OR_EQUALS)
            FeltverdiOperator.LESS_THAN.name -> (FeltverdiOperator.LESS_THAN to null)
            FeltverdiOperator.NOT_EQUALS.name -> (FeltverdiOperator.LESS_THAN to FeltverdiOperator.GREATER_THAN)
            else -> return listOf(this)
        }

        val datoverdier = verdi.map {
            try {
                LocalDate.parse(it as String)
            } catch (e: Exception) {
                null
            }
        }

        // Antar at alle verdiene er av samme type
        if (datoverdier.any { it == null }) return listOf(this)

        return datoverdier.map { oversettDateTilDateTimeIntervall(it, nedreØvreGrensebetingelse) }
    }

    fun FeltverdiOppgavefilter.oversettDateTilDateTimeIntervall(
        verdi: LocalDate?,
        nedreØvreGrensebetingelse: Pair<FeltverdiOperator?, FeltverdiOperator?>,
    ): Oppgavefilter {

        val filtre = mutableListOf<FeltverdiOppgavefilter>()
        nedreØvreGrensebetingelse.first?.let { filtre.add(copy(
            operator = it.name,
            verdi = listOf(LocalDateTime.of(verdi, LocalTime.MIN).toString())
        )) }
        nedreØvreGrensebetingelse.second?.let { filtre.add(copy(
            operator = it.name,
            verdi = listOf(LocalDateTime.of(verdi, LocalTime.MAX).toString())
        )) }

        return filtre.takeIf { it.size == 1 }?.first() ?: CombineOppgavefilter(
            combineOperator = CombineOperator.AND.name,
            filtere = filtre
        )
    }
}