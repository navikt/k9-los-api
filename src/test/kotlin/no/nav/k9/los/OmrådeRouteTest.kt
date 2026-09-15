package no.nav.k9.los

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.k9.los.infrastruktur.rest.områdeApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OmrådeRouteTest {

    @Test
    fun `ukjent område gir 404 uten å kjøre endpointet`() = testApplication {
        var endpointKalt = false

        application {
            routing {
                områdeApi {
                    get("innlogget-bruker") {
                        endpointKalt = true
                        call.respondText("Skal ikke nås")
                    }
                }
            }
        }

        val response = client.get("/ukjent/innlogget-bruker")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertFalse(endpointKalt)
    }
}
