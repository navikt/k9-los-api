package no.nav.k9.los.nyoppgavestyring.uttrekk

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgavefeltverdi
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgaverad

class UttrekkCsvGenerator {

    fun genererCsv(resultatJson: String): String {
        val oppgaverader = LosObjectMapper.instance.readValue(
            resultatJson,
            object : TypeReference<List<List<Oppgavefeltverdi>>>() {}
        )

        return genererCsv(oppgaverader)
    }

    private fun genererCsv(oppgaverader: List<Oppgaverad>): String {
        if (oppgaverader.isEmpty()) {
            return ""
        }

        val sb = StringBuilder()

        // Header - bruk kode fra første rad for å lage kolonnenavn
        val headers = oppgaverader.first().map { it.kode }
        sb.append(headers.joinToString(";"))
        sb.append("\n")

        // Data rows
        for (rad in oppgaverader) {
            val values = rad.map { feltverdi ->
                val verdi = feltverdi.verdi?.toString() ?: ""
                // Escape verdier som inneholder semikolon, newline eller anførselstegn
                if (verdi.contains(";") || verdi.contains("\n") || verdi.contains("\"")) {
                    "\"${verdi.replace("\"", "\"\"")}\""
                } else {
                    verdi
                }
            }
            sb.append(values.joinToString(";"))
            sb.append("\n")
        }

        return sb.toString()
    }
}
