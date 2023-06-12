package no.nav.k9.los.nyoppgavestyring.query.db

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import org.checkerframework.checker.units.qual.t
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object OppgavefilterDatoTypeUtvider {

    private val FELTOPERATORER_OMFATTET_AV_UTVIDELSE = setOf(
        FeltverdiOperator.EQUALS.name,
        FeltverdiOperator.LESS_THAN_OR_EQUALS.name,
        FeltverdiOperator.GREATER_THAN_OR_EQUALS.name,
        FeltverdiOperator.NOT_EQUALS.name
    )

    fun utvid(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere.flatMap { filter ->
            when (filter) {
                is FeltverdiOppgavefilter -> filter.map()
                else -> listOf(filter)
            }
        }
    }

    fun FeltverdiOppgavefilter.map(): List<Oppgavefilter> {
        val datoverdier = verdi.map {
            try {
                LocalDate.parse(it as String)
            } catch (e: Exception) {
                null
            }
        }

        return datoverdier.map { oversettDateTilDateTimeIntervall(it) }
    }

    fun FeltverdiOppgavefilter.oversettDateTilDateTimeIntervall(verdi: LocalDate?): Oppgavefilter {
        if (verdi == null) { return this }

        if (!FELTOPERATORER_OMFATTET_AV_UTVIDELSE.contains(operator)) { return this }

        return CombineOppgavefilter(
            combineOperator = CombineOperator.AND.name,
            filtere = listOf(
                this.copy(
                    operator = FeltverdiOperator.GREATER_THAN_OR_EQUALS.name,
                    verdi = listOf(LocalDateTime.of(verdi, LocalTime.MIN))
                ),
                this.copy(
                    operator = FeltverdiOperator.LESS_THAN_OR_EQUALS.name,
                    verdi = listOf(LocalDateTime.of(verdi, LocalTime.MAX))
                )
            )
        )
    }
}