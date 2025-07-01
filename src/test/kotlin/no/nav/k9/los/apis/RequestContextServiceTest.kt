package no.nav.k9.los.apis

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class RequestContextServiceTest {

    @Test
    fun `Får hentet token fra request context`() {
        testApplication {
            application {
                testApp()
            }
            
            var response = client.get("/med-request-context")
            assertEquals(HttpStatusCode.InternalServerError, response.status)

            response = client.get("/med-request-context") {
                header(HttpHeaders.Authorization, authorizationHeader(username = "Erik"))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Hei Erik", response.bodyAsText())
        }
    }

    private fun Application.testApp(
        requestContextService: RequestContextService = RequestContextService(profile = KoinProfile.PROD)
    ) {
        routing {
            route("med-request-context") {
                get {
                    kotlin.runCatching {
                        requestContextService.withRequestContext(call) {
                            coroutineContext.idToken()
                        }
                    }.fold(
                        onSuccess = {
                            call.respondText("Hei ${it.getUsername()}")
                        },
                        onFailure = {
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    )
                }
            }
        }
    }
}