package no.nav.k9.los.nyoppgavestyring.uttrekk

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.nyoppgavestyring.query.dto.query.AggregertSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Aggregeringsfunksjon
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import org.junit.jupiter.api.Test

class UttrekkCsvGeneratorTest {
    private val csvGenerator = UttrekkCsvGenerator()

    @Test
    fun `skal generere CSV med flere kolonner`() {
        val select = listOf(
            EnkelSelectFelt(område = "K9", kode = "saksnummer"),
            EnkelSelectFelt(område = "K9", kode = "behandlingstype"),
        )
        val resultatJson = """
            [
              {"id": "1", "kolonner": ["1270379", "Pleiepenger"]},
              {"id": "2", "kolonner": ["1336828", "Omsorgspenger"]}
            ]
        """.trimIndent()

        val csv = csvGenerator.genererCsv(select, resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("saksnummer,behandlingstype")
        assertThat(lines[1]).isEqualTo("1270379,Pleiepenger")
        assertThat(lines[2]).isEqualTo("1336828,Omsorgspenger")
    }

    @Test
    fun `skal håndtere null verdier`() {
        val select = listOf(
            EnkelSelectFelt(område = "K9", kode = "saksnummer"),
            EnkelSelectFelt(område = "K9", kode = "enhet"),
        )
        val resultatJson = """
            [
              {"id": "1", "kolonner": ["123456", null]}
            ]
        """.trimIndent()

        val csv = csvGenerator.genererCsv(select, resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("saksnummer,enhet")
        assertThat(lines[1]).isEqualTo("123456,")
    }

    @Test
    fun `skal returnere tom string for tomt resultat`() {
        val csv = csvGenerator.genererCsv(listOf(), "[]")
        assertThat(csv).isEqualTo("")
    }

    @Test
    fun `skal generere CSV for aggregert uttrekk`() {
        val select = listOf(
            EnkelSelectFelt(område = "K9", kode = "behandlingTypekode"),
            AggregertSelectFelt(funksjon = Aggregeringsfunksjon.ANTALL),
            AggregertSelectFelt(funksjon = Aggregeringsfunksjon.SUM, område = "K9", kode = "feilutbetaltBelop"),
        )
        val resultatJson = """
            [
              {"id": "0", "kolonner": ["BT-002", "2", "300"]}
            ]
        """.trimIndent()

        val csv = csvGenerator.genererCsv(select, resultatJson)

        val lines = csv.split("\n").filter { it.isNotEmpty() }
        assertThat(lines[0]).isEqualTo("behandlingTypekode,antall,sum_feilutbetaltBelop")
        assertThat(lines[1]).isEqualTo("BT-002,2,300")
    }
}
