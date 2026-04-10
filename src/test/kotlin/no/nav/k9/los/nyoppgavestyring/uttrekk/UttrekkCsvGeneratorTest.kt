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
                "id": "1",
                "kolonner": [
                  {
                    "kode": "saksnummer",
                    "område": "K9",
                    "verdi": "1270379"
                  },
                  {
                    "kode": "behandlingstype",
                    "område": "K9",
                    "verdi": "Pleiepenger"
                  }
                ]
              },
              {
                "id": "2",
                "kolonner": [
                  {
                    "kode": "saksnummer",
                    "område": "K9",
                    "verdi": "1336828"
                  },
                  {
                    "kode": "behandlingstype",
                    "område": "K9",
                    "verdi": "Omsorgspenger"
                  }
                ]
              }
            ]
        """.trimIndent()

        val csv = csvGenerator.genererCsv(listOf(), resultatJson)

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
                "id": "1",
                "kolonner": [
                  {
                    "kode": "saksnummer",
                    "område": "K9",
                    "verdi": "123456"
                  },
                  {
                    "kode": "enhet",
                    "område": "K9",
                    "verdi": null
                  }
                ]
              }
            ]
        """.trimIndent()

        val csv = csvGenerator.genererCsv(listOf(), resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("saksnummer,enhet")
        assertThat(lines[1]).isEqualTo("123456,")
    }

    @Test
    fun `skal returnere tom string for tomt resultat`() {
        val csv = csvGenerator.genererCsv(listOf(),"[]")
        assertThat(csv).isEqualTo("")
    }

    @Test
    fun `skal generere CSV for aggregert uttrekk`() {
        val resultatJson = """
            [
              {
                "id": "0",
                "kolonner": [
                  {
                    "kode": "behandlingTypekode",
                    "område": "K9",
                    "verdi": "BT-002"
                  },
                  {
                    "funksjon": "ANTALL",
                    "verdi": "2"
                  },
                  {
                    "kode": "feilutbetaltBelop",
                    "område": "K9",
                    "funksjon": "SUM",
                    "verdi": "300"
                  }
                ]
              }
            ]
        """.trimIndent()

        val csv = csvGenerator.genererCsv(listOf(), resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("behandlingTypekode,antall,sum_feilutbetaltBelop")
        assertThat(lines[1]).isEqualTo("BT-002,2,300")
    }
}
