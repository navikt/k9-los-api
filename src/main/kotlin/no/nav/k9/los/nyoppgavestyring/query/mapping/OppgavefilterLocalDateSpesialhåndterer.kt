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

    // Antagelse om input: alle filtere har kun en verdi, kan være null
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
            mapSomDato(filter, LocalDate.parse(verdi))
        } catch (_: DateTimeParseException) {
            filter
        }
    }
}

private fun mapSomDato(
    feltfilter: FeltverdiOppgavefilter, dato: LocalDate
): Oppgavefilter {
    val startPåDato = LocalDateTime.of(dato, LocalTime.MIN)
    val sluttPåDato = LocalDateTime.of(dato, LocalTime.MAX)

    return when (EksternFeltverdiOperator.valueOf(feltfilter.operator)) {
        EksternFeltverdiOperator.EQUALS -> CombineOppgavefilter(
            CombineOperator.AND.name, listOf(
                feltfilter.copy(
                    verdi = listOf(startPåDato),
                    operator = FeltverdiOperator.GREATER_THAN_OR_EQUALS.name
                ), feltfilter.copy(
                    verdi = listOf(sluttPåDato),
                    operator = FeltverdiOperator.LESS_THAN_OR_EQUALS.name
                )
            )
        )

        EksternFeltverdiOperator.GREATER_THAN, EksternFeltverdiOperator.LESS_THAN_OR_EQUALS -> feltfilter.copy(
            verdi = listOf(sluttPåDato)
        )

        EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, EksternFeltverdiOperator.LESS_THAN -> feltfilter.copy(
            verdi = listOf(startPåDato)
        )

        EksternFeltverdiOperator.NOT_EQUALS -> CombineOppgavefilter(
            CombineOperator.OR.name, listOf(
                feltfilter.copy(
                    verdi = listOf(startPåDato),
                    operator = FeltverdiOperator.LESS_THAN.name
                ), feltfilter.copy(
                    verdi = listOf(sluttPåDato),
                    operator = FeltverdiOperator.GREATER_THAN.name
                )
            )
        )

        else -> throw IllegalStateException("Ugyldig feltverdioperator")
    }
}

