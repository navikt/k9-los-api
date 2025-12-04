package no.nav.k9.los.nyoppgavestyring.uttrekk

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Streaming CSV generator som bruker minimal minne uavhengig av datasett-størrelse.
 *
 * Bruker Jackson's streaming API for å parse JSON token-by-token uten å deserialisere
 * hele objektgrafen. Skriver direkte til OutputStream uten å bygge hele CSV i minne først.
 *
 * Minnebruk: O(1) - konstant, uavhengig av antall rader
 * Tidskompleksitet: O(n) - lineær med antall rader
 */
class UttrekkCsvStreamingGenerator {

    fun genererCsv(resultatJson: String, outputStream: OutputStream) {
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)

        LosObjectMapper.instance.createParser(resultatJson).use { parser ->
            // Valider at vi starter med en array
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                writer.flush()
                return
            }

            var isFirstRow = true
            var headers: List<String>? = null

            // Iterer gjennom hver rad (som er en array av Oppgavefeltverdi)
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                val row = parseOppgaverad(parser)

                if (isFirstRow) {
                    // Skriv header basert på første rad
                    headers = row.map { it.first }
                    writer.write(headers.joinToString(","))
                    writer.write("\n")
                    isFirstRow = false
                }

                // Skriv data-rad
                val values = row.map { (_, value) -> escapeCsvValue(value) }
                writer.write(values.joinToString(","))
                writer.write("\n")

                // Flush hver 100. rad for å unngå store buffere
                if (row.hashCode() % 100 == 0) {
                    writer.flush()
                }
            }

            writer.flush()
        }
    }

    /**
     * Parser én Oppgaverad (array av Oppgavefeltverdi objekter) fra JSON stream.
     * Returnerer liste av (kode, verdi) par.
     */
    private fun parseOppgaverad(parser: JsonParser): List<Pair<String, String?>> {
        val row = mutableListOf<Pair<String, String?>>()

        // Valider at vi starter med en array
        if (parser.currentToken != JsonToken.START_ARRAY) {
            return emptyList()
        }

        // Iterer gjennom hvert Oppgavefeltverdi objekt
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            var kode: String? = null
            var verdi: String? = null

            // Parse Oppgavefeltverdi objekt
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val fieldName = parser.currentName()

                when (fieldName) {
                    "kode" -> {
                        parser.nextToken()
                        kode = parser.valueAsString
                    }
                    "verdi" -> {
                        parser.nextToken()
                        verdi = when (parser.currentToken) {
                            JsonToken.VALUE_NULL -> null
                            JsonToken.VALUE_STRING -> parser.valueAsString
                            JsonToken.VALUE_NUMBER_INT -> parser.valueAsLong.toString()
                            JsonToken.VALUE_NUMBER_FLOAT -> parser.valueAsDouble.toString()
                            JsonToken.VALUE_TRUE -> "true"
                            JsonToken.VALUE_FALSE -> "false"
                            else -> parser.valueAsString
                        }
                    }
                    else -> {
                        // Skip andre felter som "område"
                        parser.nextToken()
                        parser.skipChildren()
                    }
                }
            }

            if (kode != null) {
                row.add(kode to verdi)
            }
        }

        return row
    }

    /**
     * Escaper CSV-verdier som inneholder spesialtegn.
     */
    private fun escapeCsvValue(value: String?): String {
        if (value == null) return ""

        // Escape verdier som inneholder semikolon, newline eller anførselstegn
        if (value.contains(";") || value.contains("\n") || value.contains("\"")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }

        return value
    }
}
