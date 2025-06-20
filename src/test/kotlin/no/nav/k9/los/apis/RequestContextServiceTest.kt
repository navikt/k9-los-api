package no.nav.k9.los.apis

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class RequestContextServiceTest {

    @Test
    fun `Får hentet token fra request context`() {
        withTestApplication({ testApp() }) {
            handleRequest(HttpMethod.Get, "/med-request-context").apply {
                assertEquals(HttpStatusCode.InternalServerError, response.status())
            }

            handleRequest(HttpMethod.Get, "/med-request-context") {
                addHeader(HttpHeaders.Authorization, authorizationHeader(username = "Erik"))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("Hei Erik", response.content)
            }
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