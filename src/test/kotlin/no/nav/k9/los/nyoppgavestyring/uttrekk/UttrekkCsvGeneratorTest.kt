package no.nav.k9.los.nyoppgavestyring.uttrekk

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.startsWith
import org.junit.jupiter.api.Test

class UttrekkCsvGeneratorTest {

    private val csvGenerator = UttrekkCsvGenerator()

    @Test
    fun `skal generere CSV fra JSON resultat med saksnummer`() {
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

        val csv = csvGenerator.genererCsv(resultatJson)

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
    fun `skal generere CSV med flere kolonner`() {
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

        val csv = csvGenerator.genererCsv(resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("saksnummer;behandlingstype")
        assertThat(lines[1]).isEqualTo("1270379;Pleiepenger")
        assertThat(lines[2]).isEqualTo("1336828;Omsorgspenger")
    }

    @Test
    fun `skal escape verdier med semikolon`() {
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

        val csv = csvGenerator.genererCsv(resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[1]).isEqualTo("\"Dette er en tekst; med semikolon\"")
    }

    @Test
    fun `skal escape verdier med anførselstegn`() {
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

        val csv = csvGenerator.genererCsv(resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[1]).isEqualTo("\"Dette er \"\"sitert\"\" tekst\"")
    }

    @Test
    fun `skal håndtere null verdier`() {
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

        val csv = csvGenerator.genererCsv(resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("saksnummer;enhet")
        assertThat(lines[1]).isEqualTo("123456;")
    }

    @Test
    fun `skal returnere tom string for tomt resultat`() {
        val resultatJson = "[]"

        val csv = csvGenerator.genererCsv(resultatJson)

        assertThat(csv).isEqualTo("")
    }

    @Test
    fun `skal håndtere newline i verdier`() {
        val resultatJson = """
            [
              [
                {
                  "kode": "merknad",
                  "verdi": "Første linje\nAndre linje",
                  "område": null
                }
              ]
            ]
        """.trimIndent()

        val csv = csvGenerator.genererCsv(resultatJson)

        assertThat(csv).startsWith("merknad\n")
        assertThat(csv).contains("\"Første linje\nAndre linje\"")
    }
}
