package no.nav.k9.apis

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import no.nav.k9.KoinProfile
import no.nav.k9.integrasjon.rest.RequestContextService
import no.nav.k9.integrasjon.rest.idToken
import org.junit.Test
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