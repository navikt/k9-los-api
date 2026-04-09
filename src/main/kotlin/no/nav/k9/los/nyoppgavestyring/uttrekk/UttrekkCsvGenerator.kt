package no.nav.k9.los.nyoppgavestyring.uttrekk

import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgavefeltverdi

class UttrekkCsvGenerator {

    fun genererCsv(resultatJson: String): String {
        val oppgaverader = UttrekkResultatMapper.tilOppgaveResultater(resultatJson)

        return genererCsv(oppgaverader.map { it.felter })
    }

    private fun genererCsv(oppgaverader: List<List<Oppgavefeltverdi>>): String {
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
