package no.nav.k9.los.nyoppgavestyring.uttrekk

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.query.AggregertSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EksternIdSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveIdSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.dto.query.SelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.GruppertOppgaveResultat
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryResultat
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveResultat

object UttrekkResultatMapper {

    fun tilUttrekkRader(select: List<SelectFelt>, resultat: OppgaveQueryResultat): List<UttrekkRad> {
        return when (resultat) {
            is OppgaveQueryResultat.SelectResultat -> fraSelectResultat(resultat.rader)
            is OppgaveQueryResultat.GruppertResultat -> fraGruppertResultat(select, resultat.rader)
            else -> throw IllegalArgumentException("Kan ikke mappe ${resultat::class.simpleName} til uttrekk-rader.")
        }
    }

    fun fraLagretJson(resultatJson: String): List<UttrekkRad> {
        return LosObjectMapper.instance.readValue(
            resultatJson,
            object : TypeReference<List<UttrekkRad>>() {}
        )
    }

    private fun fraSelectResultat(rader: List<OppgaveResultat>): List<UttrekkRad> {
        return rader.map { oppgave ->
            UttrekkRad(
                id = oppgave.id.eksternId,
                kolonner = oppgave.felter.map { it.verdi }
            )
        }
    }

    private fun fraGruppertResultat(select: List<SelectFelt>, rader: List<GruppertOppgaveResultat>): List<UttrekkRad> {
        return rader.mapIndexed { index, rad ->
            val grupperingskolonner = rad.grupperingsverdier.associateBy { it.kode }
            val aggregeringskolonner = rad.aggregeringer.associateBy { it.type }
            val kolonner = select.map {
                when (it) {
                    is EnkelSelectFelt -> grupperingskolonner[it.kode]?.verdi
                    is AggregertSelectFelt -> aggregeringskolonner[it.funksjon]?.verdi
                    EksternIdSelectFelt -> grupperingskolonner["ekstern_id"]?.verdi
                    OppgaveIdSelectFelt -> grupperingskolonner["oppgave_id"]?.verdi
                }
            }

            UttrekkRad(
                id = index.toString(),
                kolonner = kolonner,
            )
        }
    }
}
