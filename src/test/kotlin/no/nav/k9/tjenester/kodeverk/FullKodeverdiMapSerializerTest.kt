package no.nav.k9.tjenester.kodeverk

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.fasterxml.jackson.databind.JsonNode
import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.domene.modell.KøKriterierType.FEILUTBETALING
import org.junit.jupiter.api.Test

internal class FullKodeverdiMapSerializerTest {

    private val om = objectMapper()


    @Test
    fun `skal ha med også andre felter enn JsonValue`() {
        val fullKodeverkMap = FullKodeverkMap(mapOf("KøKritererType" to listOf(FEILUTBETALING)))
        val feilutbetalingJsonNode = om.convertValue(fullKodeverkMap, JsonNode::class.java).first().first()

        assertThat(feilutbetalingJsonNode.get("navn").asText()).isEqualTo(FEILUTBETALING.navn)
        assertThat(feilutbetalingJsonNode.get("kode").asText()).isEqualTo(FEILUTBETALING.kode)
        assertThat(feilutbetalingJsonNode.get("kodeverk").asText()).isEqualTo(FEILUTBETALING.kodeverk)
        assertThat(feilutbetalingJsonNode.get("felttypeKodeverk").asText())
        assertThat(feilutbetalingJsonNode.get("skalVises").asBoolean()).isTrue()
    }

    @Test
    fun `skal ignorere JsonIgnore felter og Enum standard felter`() {
        val fullKodeverkMap = FullKodeverkMap(mapOf("KøKritererType" to listOf(FEILUTBETALING)))
        val json = om.writeValueAsString(fullKodeverkMap)

        assertThat(json).contains("kode")
        assertThat(json).doesNotContain("validator")
        assertThat(json).doesNotContain("ordinal")
        assertThat(json).doesNotContain("name")
    }

    @Test
    fun `konvertering av kodeverdi uten custom serializer skal kun returnere JsonValue`() {
        val feilutbetalingJson = om.convertValue(FEILUTBETALING, JsonNode::class.java)
        assertThat(feilutbetalingJson.asText()).isEqualTo(FEILUTBETALING.kode)
    }

}