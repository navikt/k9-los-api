package no.nav.k9.los.kodeverk

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode

/**
 * for avledning av kode for enum som ikke er mappet direkte på navn der både ny (@JsonValue) og gammel (@JsonProperty kode + kodeverk) kan
 * bli sendt. Brukes til eksisterende kode er konvertert til @JsonValue på alle grensesnitt.
 *
 * <h3>Eksempel - [BehandlingType]</h3>
 * **Gammel**: {"kode":"BT-004","kodeverk":"BEHANDLING_TYPE"}
 *
 *
 * **Ny**: "BT-004"
 *
 *
 *
 */
@Deprecated("endre grensesnitt til @JsonValue istdf @JsonProperty + @JsonCreator")
internal object TempAvledeKode {
    fun getVerdi(node: Any, key: String = "kode"): String? {
        return when (node) {
            is String -> node
            is TextNode -> node.asText()
            is JsonNode -> node[key].asText()
            is Map<*, *> -> node[key] as String?
            else -> throw IllegalArgumentException("Støtter ikke node av type: " + node.javaClass)
        }
    }
}

