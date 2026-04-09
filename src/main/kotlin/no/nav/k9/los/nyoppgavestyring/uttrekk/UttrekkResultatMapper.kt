package no.nav.k9.los.nyoppgavestyring.uttrekk

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.GruppertOppgaveResultat
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryResultat
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveResultat

object UttrekkResultatMapper {

    fun tilUttrekkRader(resultat: OppgaveQueryResultat): List<UttrekkRad> {
        return when (resultat) {
            is OppgaveQueryResultat.SelectResultat -> fraSelectResultat(resultat.rader)
            is OppgaveQueryResultat.GruppertResultat -> fraGruppertResultat(resultat.rader)
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
                kolonner = oppgave.felter.map { felt ->
                    UttrekkKolonneverdi(
                        kode = felt.kode,
                        område = felt.område,
                        verdi = felt.verdi,
                    )
                }
            )
        }
    }

    private fun fraGruppertResultat(rader: List<GruppertOppgaveResultat>): List<UttrekkRad> {
        return rader.mapIndexed { index, rad ->
            val grupperingskolonner = rad.grupperingsverdier.map { felt ->
                UttrekkKolonneverdi(
                    kode = felt.kode,
                    område = felt.område,
                    verdi = felt.verdi,
                )
            }
            val aggregeringskolonner = rad.aggregeringer.map { agg ->
                UttrekkKolonneverdi(
                    kode = agg.kode,
                    område = agg.område,
                    funksjon = agg.type,
                    verdi = agg.verdi,
                )
            }
            UttrekkRad(
                id = index.toString(),
                kolonner = grupperingskolonner + aggregeringskolonner,
            )
        }
    }
}
