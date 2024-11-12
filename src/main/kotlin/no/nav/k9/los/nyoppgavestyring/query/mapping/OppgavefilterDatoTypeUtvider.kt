package no.nav.k9.los.nyoppgavestyring.query.mapping

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
            }
        }
    }

    private fun FeltverdiOppgavefilter.map(): List<Oppgavefilter> {

        // Oversetter operator til pair med grensebetingelser. null tilsvarer fravær av grensebetingelse
        // Gjøres for å mappe grensebetingelser individuelt for hver verdi - fremfor alle nedre grenser, deretter alle øvre grenser.
        val nedreØvreGrensebetingelse = when (EksternFeltverdiOperator.valueOf(operator)) {
            EksternFeltverdiOperator.EQUALS,
            EksternFeltverdiOperator.IN -> (FeltverdiOperator.GREATER_THAN_OR_EQUALS to FeltverdiOperator.LESS_THAN_OR_EQUALS)
            EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS -> (FeltverdiOperator.GREATER_THAN_OR_EQUALS to null)
            EksternFeltverdiOperator.GREATER_THAN -> (null to FeltverdiOperator.GREATER_THAN)
            EksternFeltverdiOperator.LESS_THAN_OR_EQUALS -> (null to FeltverdiOperator.LESS_THAN_OR_EQUALS)
            EksternFeltverdiOperator.LESS_THAN -> (FeltverdiOperator.LESS_THAN to null)
            EksternFeltverdiOperator.NOT_EQUALS,
            EksternFeltverdiOperator.NOT_IN -> (FeltverdiOperator.LESS_THAN to FeltverdiOperator.GREATER_THAN)
            else -> return listOf(this)
        }

        val datoverdier = verdi.map {
            try {
                LocalDateTime.parse(it as String)
            } catch (e: Exception) { null } ?:
            try {
                LocalDate.parse(it as String)
            } catch (e: Exception) { null }
        }

        // Antar at alle verdiene er av samme type
        return when {
            datoverdier.any { it == null } -> listOf(this)
            datoverdier.any { it is LocalDateTime } -> listOf(this)
            else -> datoverdier.map { oversettDateTilDateTimeIntervall(it as LocalDate, nedreØvreGrensebetingelse) }
        }
    }

    private fun FeltverdiOppgavefilter.oversettDateTilDateTimeIntervall(
        dato: LocalDate,
        nedreØvreGrensebetingelse: Pair<FeltverdiOperator?, FeltverdiOperator?>,
    ): Oppgavefilter {
        val (nedreGrense, øvreGrense) = nedreØvreGrensebetingelse

        val filtre = mutableListOf<FeltverdiOppgavefilter>()
        kopiMedFelter(nedreGrense, LocalTime.MIN, dato)?.let { filtre.add(it) }
        kopiMedFelter(øvreGrense, LocalTime.MAX, dato)?.let { filtre.add(it) }

        return filtre.takeIf { it.size == 1 }?.first() ?: CombineOppgavefilter(
            combineOperator = hentCombineoperator().name,
            filtere = filtre
        )
    }

    private fun FeltverdiOppgavefilter.hentCombineoperator() =
        when (operator) {
            FeltverdiOperator.NOT_EQUALS.name,
            FeltverdiOperator.NOT_IN.name -> CombineOperator.OR
            else -> CombineOperator.AND
        }

    private fun FeltverdiOppgavefilter.kopiMedFelter(
        operator: FeltverdiOperator?,
        tidsgrense: LocalTime,
        verdi: LocalDate?
    ): FeltverdiOppgavefilter? {
        if (operator == null) return null

        return copy(
            operator = operator.name,
            verdi = listOf(LocalDateTime.of(verdi, tidsgrense).toString())
        )
    }
}