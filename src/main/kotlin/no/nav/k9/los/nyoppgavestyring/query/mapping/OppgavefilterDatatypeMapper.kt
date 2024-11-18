package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefeltMedMer
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter
import org.postgresql.util.PGInterval
import java.math.BigInteger
import java.time.LocalDateTime
import kotlin.time.Duration

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
        val verdier = filter.verdi.map { verdi ->
            if (verdi == null || verdi !is String) {
                verdi // er enten null eller har blitt konvertert tidligere
            } else {
                when (datatype) {
                    Datatype.INTEGER -> verdi.toBigInteger()
                    Datatype.DURATION -> PGInterval(verdi)
                    Datatype.TIMESTAMP -> LocalDateTime.parse(verdi)
                    Datatype.BOOLEAN -> verdi.toBoolean()
                    else -> verdi
                }
            }
        }
        return filter.copy(verdi = verdier)
    }
}
