package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.query.dto.query.*

class UttrekkCsvGenerator {

    fun genererCsv(select: List<SelectFelt>, resultatJson: String): String {
        val rader = UttrekkResultatMapper.fraLagretJson(resultatJson)

        if (rader.isEmpty()) {
            return ""
        }

        return buildString {
            val headers = select.map {
                when (it) {
                    is EnkelSelectFelt -> it.kode
                    is AggregertSelectFelt -> {
                        val funksjonNavn = it.funksjon.name.lowercase()
                        if (it.kode != null) "${funksjonNavn}_${it.kode}" else funksjonNavn
                    }
                    EksternIdSelectFelt -> "ekstern_id"
                    OppgaveIdSelectFelt -> "oppgave_id"
                }
            }
            append(headers.joinToString(","))
            append("\n")

            for (rad in rader) {
                val values = rad.kolonner.map { kolonne ->
                    csvEscape(kolonne?.toString() ?: "")
                }
                append(values.joinToString(","))
                append("\n")
            }
        }
    }

    private fun csvEscape(verdi: String): String {
        return if (verdi.contains(',') || verdi.contains('"') || verdi.contains('\n')) {
            "\"${verdi.replace("\"", "\"\"")}\""
        } else {
            verdi
        }
    }
}
