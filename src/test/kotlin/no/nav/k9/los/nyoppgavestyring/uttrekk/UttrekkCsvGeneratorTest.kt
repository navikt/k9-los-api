package no.nav.k9.los.nyoppgavestyring.uttrekk

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class UttrekkCsvGeneratorTest {
    private val csvGenerator = UttrekkCsvGenerator()

    @Test
    fun `skal generere CSV med flere kolonner`() {
        val resultatJson = """
            [
              {
                "id": {"område": "K9", "eksternId": "1"},
                "felter": [
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
                ]
              },
              {
                "id": {"område": "K9", "eksternId": "2"},
                "felter": [
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
              }
            ]
        """.trimIndent()

        val csv = csvGenerator.genererCsv(resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("saksnummer,behandlingstype")
        assertThat(lines[1]).isEqualTo("1270379,Pleiepenger")
        assertThat(lines[2]).isEqualTo("1336828,Omsorgspenger")
    }

    @Test
    fun `skal håndtere null verdier`() {
        val resultatJson = """
            [
              {
                "id": {"område": "K9", "eksternId": "1"},
                "felter": [
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
              }
            ]
        """.trimIndent()

        val csv = csvGenerator.genererCsv(resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("saksnummer,enhet")
        assertThat(lines[1]).isEqualTo("123456,")
    }

    @Test
    fun `skal returnere tom string for tomt resultat`() {
        val csv = csvGenerator.genererCsv("[]")
        assertThat(csv).isEqualTo("")
    }
}
