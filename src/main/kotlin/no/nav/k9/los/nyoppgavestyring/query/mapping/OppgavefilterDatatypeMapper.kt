package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefeltMedMer
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import org.postgresql.util.PGInterval

object OppgavefilterDatatypeMapper {
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
                    datatype == Datatype.DURATION -> PGInterval(verdi)
                    // Datatype.TIMESTAMP -> filter.copy(verdi = listOf(LocalDateTime.parse(verdi)))
                    // Datatype.BOOLEAN -> filter.copy(verdi = listOf(verdi.toBoolean()))
                    else -> verdi
                }
            }
        )
    }
}
