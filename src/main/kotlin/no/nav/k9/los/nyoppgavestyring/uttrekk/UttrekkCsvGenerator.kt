package no.nav.k9.los.nyoppgavestyring.uttrekk

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveResultat
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgavefeltverdi
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgaverad

class UttrekkCsvGenerator {

    fun genererCsv(resultatJson: String): String {
        val oppgaverader = LosObjectMapper.instance.readValue(
            resultatJson,
            object : TypeReference<List<OppgaveResultat>>() {}
        )

        return genererCsv(oppgaverader.map { it.felter })
    }

    private fun genererCsv(oppgaverader: List<Oppgaverad>): String {
        if (oppgaverader.isEmpty()) {
            return ""
        }

        return buildString {
            // Kode fra første rad som kolonnenavn
            // Bruke feltdefinisjon for å hente visningsnavn er en mulig forbedring
            val headers = oppgaverader.first().map { it.kode }
            append(headers.joinToString(","))
            append("\n")

            for (rad in oppgaverader) {
                val values = rad.map { feltverdi ->
                    feltverdi.verdi?.toString() ?: ""
                }
                append(values.joinToString(","))
                append("\n")
            }
        }
    }
}
