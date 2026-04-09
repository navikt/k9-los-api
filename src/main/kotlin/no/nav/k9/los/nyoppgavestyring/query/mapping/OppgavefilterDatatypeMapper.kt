package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefeltMedMer
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import org.postgresql.util.PGInterval
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.format.DateTimeParseException

object OppgavefilterDatatypeMapper {
    private val log = LoggerFactory.getLogger(OppgavefilterDatatypeMapper::class.java)

    fun map(felter: Map<OmrådeOgKode, OppgavefeltMedMer>, oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere.map { filter ->
            when (filter) {
                is FeltverdiOppgavefilter -> mapFeltverdiFilter(felter, filter)
                is CombineOppgavefilter -> CombineOppgavefilter(
                    combineOperator = filter.combineOperator, map(felter, filter.filtere)
                )
            }
        }
    }

    private fun mapFeltverdiFilter(
        felter: Map<OmrådeOgKode, OppgavefeltMedMer>,
        filter: FeltverdiOppgavefilter
    ): FeltverdiOppgavefilter {
        val datatype = felter[OmrådeOgKode(filter.område, filter.kode)]?.oppgavefelt?.tolkes_som
            ?.let { Datatype.fraKode(it) }

        return filter.copy(
            verdi = filter.verdi.map { verdi ->
                when {
                    verdi == null || verdi !is String -> verdi
                    datatype == Datatype.INTEGER -> verdi.toLong()
                    datatype == Datatype.DURATION -> iso8601TilPGInterval(verdi)
                    // Datatype.TIMESTAMP -> filter.copy(verdi = listOf(LocalDateTime.parse(verdi)))
                    // Datatype.BOOLEAN -> filter.copy(verdi = listOf(verdi.toBoolean()))
                    else -> verdi
                }
            }
        )
    }

    private fun iso8601TilPGInterval(iso8601: String): PGInterval {
        val duration = try {
            Duration.parse(iso8601)
        } catch (e: DateTimeParseException) {
            log.warn("Kunne ikke parse duration fra '{}'", iso8601)
            throw e
        }
        val totalSeconds = duration.seconds
        val days = (totalSeconds / 86400).toInt()
        val hours = ((totalSeconds % 86400) / 3600).toInt()
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = (totalSeconds % 60).toDouble()
        return PGInterval(0, 0, days, hours, minutes, seconds)
    }
}
