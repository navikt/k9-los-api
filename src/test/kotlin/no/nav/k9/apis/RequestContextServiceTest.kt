package no.nav.k9.apis

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.k9.KoinProfile
import no.nav.k9.integrasjon.rest.RequestContextService
import no.nav.k9.integrasjon.rest.idToken
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
        requestContextService: RequestContextService = RequestContextService(profile = KoinProfile.PROD)) {
        routing {
            route("med-request-context") {
                get {
                    kotlin.runCatching { requestContextService.withRequestContext(call) {
                        coroutineContext.idToken()
                    }}.fold(
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