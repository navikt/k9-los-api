package no.nav.k9.apis

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.core.DefaultStatusPages
import no.nav.helse.dusseldorf.ktor.core.logRequests
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.testsupport.jws.Azure
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.dusseldorf.testsupport.wiremock.getAzureV2JwksUrl
import org.junit.Test
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
            sendJsonRequest(forventetHttpResponseCode = HttpStatusCode.Forbidden, authorizationHeader = authorizationHeader(
                audience = "feil-audience"
            ))
        }

        wireMock.stop()
    }

    private fun TestApplicationEngine.sendJsonRequest(
        authorizationHeader: String = authorizationHeader(),
        forventetHttpResponseCode: HttpStatusCode) {
        handleRequest(HttpMethod.Post, "/test"){
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
        cors: Boolean) {
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

        install(Authentication){
            multipleJwtIssuers(issuers)
        }

        routing {
            if (cors) {
                install(CORS) {
                    method(HttpMethod.Options)
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