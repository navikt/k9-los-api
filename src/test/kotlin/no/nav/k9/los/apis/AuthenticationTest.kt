package no.nav.k9.los.apis

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.helse.dusseldorf.ktor.auth.AuthStatusPages
import no.nav.helse.dusseldorf.ktor.auth.Issuer
import no.nav.helse.dusseldorf.ktor.auth.allIssuers
import no.nav.helse.dusseldorf.ktor.auth.multipleJwtIssuers
import no.nav.helse.dusseldorf.ktor.auth.withoutAdditionalClaimRules
import no.nav.helse.dusseldorf.ktor.core.DefaultStatusPages
import no.nav.helse.dusseldorf.ktor.core.logRequests
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.testsupport.jws.Azure
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.dusseldorf.testsupport.wiremock.getAzureV2JwksUrl
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals

class AuthenticationTest {

    @Test
    fun `POST request med og uten CORS`() {
        val wireMock = WireMockBuilder().withAzureSupport().build()

        withTestApplication({ testApp(wireMock = wireMock, cors = true) }) {
            sendJsonRequest(forventetHttpResponseCode = HttpStatusCode.Forbidden)
        }

        withTestApplication({ testApp(wireMock = wireMock, cors = false) }) {
            sendJsonRequest(forventetHttpResponseCode = HttpStatusCode.NoContent)
            sendJsonRequest(
                forventetHttpResponseCode = HttpStatusCode.Forbidden, authorizationHeader = authorizationHeader(
                    audience = "feil-audience"
                )
            )
        }

        wireMock.stop()
    }

    private fun TestApplicationEngine.sendJsonRequest(
        authorizationHeader: String = authorizationHeader(),
        forventetHttpResponseCode: HttpStatusCode
    ) {
        handleRequest(HttpMethod.Post, "/test") {
            addHeader(HttpHeaders.Authorization, authorizationHeader)
            addHeader(HttpHeaders.ContentType, "application/json")
            addHeader(HttpHeaders.Origin, "https://k9-los.nav.no")
            setBody("""{ "test": true }""".trimIndent())
        }.apply {
            assertEquals(forventetHttpResponseCode, response.status())
        }
    }

    private fun Application.testApp(
        wireMock: WireMockServer,
        cors: Boolean
    ) {
        install(CallLogging) {
            logRequests()
        }
        install(StatusPages) {
            DefaultStatusPages()
            JacksonStatusPages()
            AuthStatusPages()
        }

        val azureV2 = Issuer(
            issuer = Azure.V2_0.getIssuer(),
            jwksUri = URI(wireMock.getAzureV2JwksUrl()),
            audience = "k9-los-api",
            alias = "azure-v2"
        )

        val issuers = mapOf(
            azureV2.alias() to azureV2,
        ).withoutAdditionalClaimRules()

        install(Authentication) {
            multipleJwtIssuers(issuers)
        }

        routing {
            if (cors) {
                install(CORS) {
                    allowMethod(HttpMethod.Options)
                    anyHost()
                    allowCredentials = true
                }
            }
            authenticate(*issuers.allIssuers()) {
                route("test") {
                    post {
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}