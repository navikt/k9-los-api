package no.nav.k9.los.nyoppgavestyring.uttrekk

class UttrekkCsvGenerator {

    fun genererCsv(resultatJson: String): String {
        val rader = UttrekkResultatMapper.fraLagretJson(resultatJson)

        if (rader.isEmpty()) {
            return ""
        }

        return buildString {
            val headers = rader.first().kolonner.map { it.csvKolonnenavn() }
            append(headers.joinToString(","))
            append("\n")

            for (rad in rader) {
                val values = rad.kolonner.map { kolonne ->
                    kolonne.verdi?.toString() ?: ""
                }
                append(values.joinToString(","))
                append("\n")
            }
        }
    }
}
