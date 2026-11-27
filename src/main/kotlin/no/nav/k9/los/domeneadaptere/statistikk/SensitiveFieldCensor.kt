package no.nav.k9.los.domeneadaptere.statistikk

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.k9.los.infrastruktur.utils.SensitiveField
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper

object SensitiveFieldCensor {
    private val objectMapper = LosObjectMapper.instance

    fun <T : Any> censor(value: T): T {
        val type = objectMapper.typeFactory.constructType(value.javaClass)
        val node = objectMapper.valueToTree<JsonNode>(value)
        censor(node, type, replacement = null)
        return objectMapper.treeToValue(node, value.javaClass)
    }

    private fun censor(node: JsonNode, type: JavaType, replacement: String?): JsonNode {
        when (node) {
            is ObjectNode -> {
                objectMapper.serializationConfig.introspect(type).findProperties().forEach { property ->
                    val fieldReplacement = replacement
                        ?: property.primaryMember?.getAnnotation(SensitiveField::class.java)?.replacement
                    val child = node.get(property.name)
                    if (child == null) {
                        if (fieldReplacement != null) {
                            node.set<JsonNode>(property.name, placeholder(property.primaryType, fieldReplacement))
                        }
                        return@forEach
                    }
                    node.set<JsonNode>(property.name, censor(child, property.primaryType, fieldReplacement))
                }
                return node
            }
            is ArrayNode -> {
                val elementType = type.contentType ?: return node
                for (index in 0 until node.size()) {
                    node.set(index, censor(node[index], elementType, replacement))
                }
                return node
            }
            else -> return if (replacement != null) placeholder(type, replacement) else node
        }
    }

    private fun placeholder(type: JavaType, replacement: String): JsonNode {
        return when {
            type.isTypeOrSubTypeOf(String::class.java) -> JsonNodeFactory.instance.textNode(replacement)
            type.isTypeOrSubTypeOf(Number::class.java) ||
                (type.rawClass.isPrimitive && type.rawClass != Boolean::class.javaPrimitiveType && type.rawClass != Char::class.javaPrimitiveType) ->
                JsonNodeFactory.instance.numberNode(replacement.toBigDecimalOrNull() ?: -5.toBigDecimal())
            else -> throw IllegalArgumentException("Unsupported @SensitiveField type: ${type.rawClass.name}")
        }
    }
}
