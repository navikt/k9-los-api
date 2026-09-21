package no.nav.k9.los

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.k9.los.infrastruktur.rest.OmrådeUrlSegment
import no.nav.k9.los.infrastruktur.rest.områdeApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenApiSpecificationTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `frontend og forvaltning eksponeres i separate spesifikasjoner`() = testApplication {
        application {
            install(OpenApi, io.github.smiley4.ktoropenapi.config.OpenApiPluginConfig::k9LosOpenApiConfig)
            routing {
                route("legacy", {
                    specName = "forvaltning"
                    operationId = "legacyOperasjon"
                }) {
                    get { call.respondText("legacy") }
                }
                route("api/fleromrade", {
                    specName = "frontend"
                    protected = true
                    request {
                        pathParameter<OmrådeUrlSegment>("omrade")
                    }
                }) {
                    områdeApi {
                        get("ressurs", {
                            operationId = "frontendOperasjon"
                        }) {
                            call.respondText("wip")
                        }
                    }
                }
                route("legacy-openapi.json") { openApi("forvaltning") }
                route("frontend-openapi.json") { openApi("frontend") }
            }
        }

        val legacy: JsonNode = objectMapper.readTree(client.get("/legacy-openapi.json").bodyAsText())
        val frontend: JsonNode = objectMapper.readTree(client.get("/frontend-openapi.json").bodyAsText())

        assertTrue(legacy["paths"].has("/legacy"))
        assertFalse(legacy["paths"].has("/api/fleromrade/{omrade}/ressurs"))
        assertEquals("legacyOperasjon", legacy.operation("/legacy")["operationId"].textValue())

        assertFalse(frontend["paths"].has("/legacy"))
        val frontendOperation = frontend.operation("/api/fleromrade/{omrade}/ressurs")
        assertEquals("frontendOperasjon", frontendOperation["operationId"].textValue())
        val områdeSchema = frontend.resolveSchema(frontendOperation.parameter("omrade")["schema"])
        assertEquals(listOf("k9", "akt"), områdeSchema["enum"].map(JsonNode::textValue))
    }

    private fun JsonNode.operation(path: String): JsonNode {
        val pathItem = this["paths"][path]
        return pathItem.fields().asSequence().first { it.key in HTTP_METHODS }.value
    }

    private fun JsonNode.parameter(name: String): JsonNode =
        this["parameters"].first { it["name"].textValue() == name }

    private fun JsonNode.resolveSchema(schema: JsonNode): JsonNode {
        val reference = schema["\$ref"]?.textValue() ?: return schema
        return reference.removePrefix("#/").split("/").fold(this) { node, segment -> node[segment] }
    }

    private companion object {
        val HTTP_METHODS = setOf("get", "post", "put", "patch", "delete", "options", "head")
    }
}
