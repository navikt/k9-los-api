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
        val verdi = filter.verdi[0]
        if (verdi == null || verdi !is String) return filter // er enten null eller har blitt konvertert tidligere
        return when (datatype) {
            Datatype.INTEGER -> filter.copy(verdi = listOf(verdi.toLong()))
            Datatype.DURATION -> filter.copy(verdi = listOf(PGInterval(verdi)))
            // Datatype.TIMESTAMP -> filter.copy(verdi = listOf(LocalDateTime.parse(verdi)))
            // Datatype.BOOLEAN -> filter.copy(verdi = listOf(verdi.toBoolean()))
            else -> filter
        }
    }
}
