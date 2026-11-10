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
        censor(node, type, force = false)
        return objectMapper.treeToValue(node, value.javaClass)
    }

    private fun censor(node: JsonNode, type: JavaType, force: Boolean): JsonNode {
        when (node) {
            is ObjectNode -> {
                objectMapper.serializationConfig.introspect(type).findProperties().forEach { property ->
                    val sensitive = force || property.primaryMember?.hasAnnotation(SensitiveField::class.java) == true
                    val child = node.get(property.name)
                    if (child == null) {
                        if (sensitive) node.set<JsonNode>(property.name, placeholder(property.primaryType))
                        return@forEach
                    }
                    node.set<JsonNode>(property.name, censor(child, property.primaryType, sensitive))
                }
                return node
            }
            is ArrayNode -> {
                val elementType = type.contentType ?: return node
                for (index in 0 until node.size()) {
                    node.set(index, censor(node[index], elementType, force))
                }
                return node
            }
            else -> return if (force) placeholder(type) else node
        }
    }

    private fun placeholder(type: JavaType): JsonNode {
        return when {
            type.isTypeOrSubTypeOf(String::class.java) -> JsonNodeFactory.instance.textNode("-5")
            type.isTypeOrSubTypeOf(Number::class.java) ||
                (type.rawClass.isPrimitive && type.rawClass != Boolean::class.javaPrimitiveType && type.rawClass != Char::class.javaPrimitiveType) ->
                JsonNodeFactory.instance.numberNode(-5)
            else -> throw IllegalArgumentException("Unsupported @SensitiveField type: ${type.rawClass.name}")
        }
    }
}
