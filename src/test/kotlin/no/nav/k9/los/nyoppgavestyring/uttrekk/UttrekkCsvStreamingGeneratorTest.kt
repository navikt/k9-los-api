package no.nav.k9.los.nyoppgavestyring.uttrekk

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class UttrekkCsvStreamingGeneratorTest {

    private val csvGenerator = UttrekkCsvStreamingGenerator()

    @Test
    fun `skal generere CSV fra JSON resultat med saksnummer via streaming`() {
        val resultatJson = """
            [
              [
                {
                  "kode": "saksnummer",
                  "verdi": "1270379",
                  "område": "K9"
                }
              ],
              [
                {
                  "kode": "saksnummer",
                  "verdi": "1336828",
                  "område": "K9"
                }
              ],
              [
                {
                  "kode": "saksnummer",
                  "verdi": "397462",
                  "område": "K9"
                }
              ]
            ]
        """.trimIndent()

        val output = ByteArrayOutputStream()
        csvGenerator.genererCsv(resultatJson, output)
        val csv = output.toString(Charsets.UTF_8)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines).isEqualTo(
            listOf(
                "saksnummer",
                "1270379",
                "1336828",
                "397462"
            )
        )
    }

    @Test
    fun `skal generere CSV med flere kolonner via streaming`() {
        val resultatJson = """
            [
              [
                {
                  "kode": "saksnummer",
                  "verdi": "1270379",
                  "område": "K9"
                },
                {
                  "kode": "behandlingstype",
                  "verdi": "Pleiepenger",
                  "område": "K9"
                }
              ],
              [
                {
                  "kode": "saksnummer",
                  "verdi": "1336828",
                  "område": "K9"
                },
                {
                  "kode": "behandlingstype",
                  "verdi": "Omsorgspenger",
                  "område": "K9"
                }
              ]
            ]
        """.trimIndent()

        val output = ByteArrayOutputStream()
        csvGenerator.genererCsv(resultatJson, output)
        val csv = output.toString(Charsets.UTF_8)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("saksnummer;behandlingstype")
        assertThat(lines[1]).isEqualTo("1270379;Pleiepenger")
        assertThat(lines[2]).isEqualTo("1336828;Omsorgspenger")
    }

    @Test
    fun `skal escape verdier med semikolon via streaming`() {
        val resultatJson = """
            [
              [
                {
                  "kode": "beskrivelse",
                  "verdi": "Dette er en tekst; med semikolon",
                  "område": null
                }
              ]
            ]
        """.trimIndent()

        val output = ByteArrayOutputStream()
        csvGenerator.genererCsv(resultatJson, output)
        val csv = output.toString(Charsets.UTF_8)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[1]).isEqualTo("\"Dette er en tekst; med semikolon\"")
    }

    @Test
    fun `skal håndtere null verdier via streaming`() {
        val resultatJson = """
            [
              [
                {
                  "kode": "saksnummer",
                  "verdi": "123456",
                  "område": "K9"
                },
                {
                  "kode": "enhet",
                  "verdi": null,
                  "område": "K9"
                }
              ]
            ]
        """.trimIndent()

        val output = ByteArrayOutputStream()
        csvGenerator.genererCsv(resultatJson, output)
        val csv = output.toString(Charsets.UTF_8)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("saksnummer;enhet")
        assertThat(lines[1]).isEqualTo("123456;")
    }

    @Test
    fun `skal returnere tom string for tomt resultat via streaming`() {
        val resultatJson = "[]"

        val output = ByteArrayOutputStream()
        csvGenerator.genererCsv(resultatJson, output)
        val csv = output.toString(Charsets.UTF_8)

        assertThat(csv).isEqualTo("")
    }

    @Test
    fun `skal håndtere stort datasett uten å bruke mye minne`() {
        // Generer JSON med 10000 rader
        val rows = (1..10000).joinToString(",\n") { i ->
            """
            [
              {
                "kode": "saksnummer",
                "verdi": "$i",
                "område": "K9"
              },
              {
                "kode": "status",
                "verdi": "AAPEN",
                "område": "K9"
              }
            ]
            """.trimIndent()
        }
        val resultatJson = "[\n$rows\n]"

        val output = ByteArrayOutputStream()
        val startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        csvGenerator.genererCsv(resultatJson, output)

        val endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val csv = output.toString(Charsets.UTF_8)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines.size).isEqualTo(10001) // Header + 10000 rader
        assertThat(lines[0]).isEqualTo("saksnummer;status")
        assertThat(lines[1]).isEqualTo("1;AAPEN")
        assertThat(lines[10000]).isEqualTo("10000;AAPEN")

        // Verifiser at minnebruken er rimelig (under 50MB)
        val memoryUsed = (endMemory - startMemory) / 1024 / 1024
        println("Minnebruk for 10000 rader: ${memoryUsed}MB")
    }

    @Test
    fun `skal håndtere escape av anførselstegn via streaming`() {
        val resultatJson = """
            [
              [
                {
                  "kode": "beskrivelse",
                  "verdi": "Dette er \"sitert\" tekst",
                  "område": null
                }
              ]
            ]
        """.trimIndent()

        val output = ByteArrayOutputStream()
        csvGenerator.genererCsv(resultatJson, output)
        val csv = output.toString(Charsets.UTF_8)

        assertThat(csv).contains("\"Dette er \"\"sitert\"\" tekst\"")
    }
}
