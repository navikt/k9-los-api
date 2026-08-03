package no.nav.k9.los.apis

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.helse.dusseldorf.ktor.auth.*
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

        testApplication {
            application {
                testApp(wireMock = wireMock, cors = true)
            }
            sendJsonRequest(forventetHttpResponseCode = HttpStatusCode.Forbidden)
        }

        testApplication {
            application {
                testApp(wireMock = wireMock, cors = false)
            }
            sendJsonRequest(forventetHttpResponseCode = HttpStatusCode.NoContent)
            sendJsonRequest(
                forventetHttpResponseCode = HttpStatusCode.Forbidden, authorizationHeader = authorizationHeader(
                    audience = "feil-audience"
                )
            )
        }

        wireMock.stop()
    }

    private suspend fun ApplicationTestBuilder.sendJsonRequest(
        authorizationHeader: String = authorizationHeader(),
        forventetHttpResponseCode: HttpStatusCode
    ) {
        val response = client.post("/test") {
            header(HttpHeaders.Authorization, authorizationHeader)
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Origin, "https://k9-los.nav.no")
            setBody("""{ "test": true }""".trimIndent())
        }
        assertEquals(forventetHttpResponseCode, response.status)
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