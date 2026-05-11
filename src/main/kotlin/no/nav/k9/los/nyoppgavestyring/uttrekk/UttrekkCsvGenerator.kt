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
                }
            }
            append(headers.joinToString(","))
            append("\n")

            for (rad in rader) {
                val values = rad.kolonner.map { kolonne ->
                    csvEscape(tilCsvVerdi(kolonne))
                }
                append(values.joinToString(","))
                append("\n")
            }
        }
    }

    private fun tilCsvVerdi(verdi: Any?): String {
        return when (verdi) {
            null -> ""
            is List<*> -> verdi.joinToString(", ")
            else -> verdi.toString()
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
