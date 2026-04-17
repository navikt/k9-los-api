package no.nav.k9.los.nyoppgavestyring.uttrekk

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.query.AggregertSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EksternIdSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveIdSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.SelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryRad

object UttrekkResultatMapper {

    fun tilUttrekkRader(select: List<SelectFelt>, rader: List<OppgaveQueryRad>): List<UttrekkRad> {
        return rader.mapIndexed { index, rad ->
            val feltverdierMap = rad.feltverdier.associateBy { it.kode }
            val aggregeringerMap = rad.aggregeringer.associateBy { it.type }
            val kolonner = select.map {
                when (it) {
                    is EnkelSelectFelt -> feltverdierMap[it.kode]?.verdi
                    is AggregertSelectFelt -> aggregeringerMap[it.funksjon]?.verdi
                    EksternIdSelectFelt -> feltverdierMap["ekstern_id"]?.verdi
                    OppgaveIdSelectFelt -> feltverdierMap["oppgave_id"]?.verdi
                }
            }

            UttrekkRad(
                id = index.toString(),
                kolonner = kolonner,
            )
        }
    }

    fun fraLagretJson(resultatJson: String): List<UttrekkRad> {
        return LosObjectMapper.instance.readValue(
            resultatJson,
            object : TypeReference<List<UttrekkRad>>() {}
        )
    }
}
