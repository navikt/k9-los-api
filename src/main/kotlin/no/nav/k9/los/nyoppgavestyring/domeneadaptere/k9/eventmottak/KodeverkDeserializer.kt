package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.JsonNodeType
import java.io.IOException

class KodeverkDeserializer : StdDeserializer<List<String>>(KodeverkDeserializer::class.java) {

    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<String> {
        val oc = p.codec
        val node = oc.readTree<JsonNode>(p)

        if (node.isNull) return emptyList()

        return if (node.nodeType == JsonNodeType.ARRAY) {
            node.map { element ->
                parseElement(element)
            }
        } else {
            listOf(parseElement(node))
        }
    }

    fun parseElement(node: JsonNode): String {
        if (node.nodeType == JsonNodeType.OBJECT) {
            return node["kode"].asText()
        }
        return node.asText()
    }
}