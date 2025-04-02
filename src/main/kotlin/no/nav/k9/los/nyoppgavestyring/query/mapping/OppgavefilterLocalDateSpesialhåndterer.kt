package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

// Vurder om det skal være spesialhåndtering av datoer. Nødvendig hvis vi kun har timestamp.
object OppgavefilterLocalDateSpesialhåndterer {

    // Antagelse om input: alle filtere har kun en verdi bortsett fra interval som har to
    fun spesialhåndter(
        oppgavefiltere: List<Oppgavefilter>
    ): List<Oppgavefilter> {
        return oppgavefiltere.map { filter ->
            when (filter) {
                is FeltverdiOppgavefilter -> mapFeltverdiFilter(filter)
                is CombineOppgavefilter -> CombineOppgavefilter(
                    combineOperator = filter.combineOperator, spesialhåndter(filter.filtere)
                )
            }
        }
    }

    private fun mapFeltverdiFilter(filter: FeltverdiOppgavefilter): Oppgavefilter {
        val verdi = filter.verdi[0]
        if (verdi !is String) {
            return filter
        }
        return try {
            mapSomDato(filter, LocalDate.parse(verdi), if (filter.verdi.size == 2) LocalDate.parse(filter.verdi[1] as String) else null)
        } catch (_: DateTimeParseException) {
            filter
        }
    }
}

private fun mapSomDato(
    feltfilter: FeltverdiOppgavefilter, dato1: LocalDate, dato2: LocalDate?
): Oppgavefilter {
    val startPåDato1 = LocalDateTime.of(dato1, LocalTime.MIN)
    val sluttPåDato1 = LocalDateTime.of(dato1, LocalTime.MAX)

    val startPåDato2 = dato2?.let { LocalDateTime.of(it, LocalTime.MIN) }
    val sluttPåDato2 = dato2?.let { LocalDateTime.of(it, LocalTime.MAX) }

    return when (EksternFeltverdiOperator.valueOf(feltfilter.operator)) {
        EksternFeltverdiOperator.EQUALS, EksternFeltverdiOperator.IN -> CombineOppgavefilter(
            CombineOperator.AND.name, listOf(
                feltfilter.copy(
                    verdi = listOf(startPåDato1),
                    operator = FeltverdiOperator.GREATER_THAN_OR_EQUALS.name
                ), feltfilter.copy(
                    verdi = listOf(sluttPåDato1),
                    operator = FeltverdiOperator.LESS_THAN_OR_EQUALS.name
                )
            )
        )

        EksternFeltverdiOperator.GREATER_THAN, EksternFeltverdiOperator.LESS_THAN_OR_EQUALS -> feltfilter.copy(
            verdi = listOf(sluttPåDato1)
        )

        EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, EksternFeltverdiOperator.LESS_THAN -> feltfilter.copy(
            verdi = listOf(startPåDato1)
        )

        EksternFeltverdiOperator.NOT_EQUALS, EksternFeltverdiOperator.NOT_IN -> CombineOppgavefilter(
            CombineOperator.OR.name, listOf(
                feltfilter.copy(
                    verdi = listOf(startPåDato1),
                    operator = FeltverdiOperator.LESS_THAN.name
                ), feltfilter.copy(
                    verdi = listOf(sluttPåDato1),
                    operator = FeltverdiOperator.GREATER_THAN.name
                )
            )
        )

        EksternFeltverdiOperator.INTERVAL -> CombineOppgavefilter(
            CombineOperator.AND.name, listOf(
                feltfilter.copy(
                    verdi = listOf(startPåDato1),
                    operator = FeltverdiOperator.GREATER_THAN_OR_EQUALS.name
                ), feltfilter.copy(
                    verdi = listOf(sluttPåDato2),
                    operator = FeltverdiOperator.LESS_THAN_OR_EQUALS.name
                )
            )
        )
    }
}

