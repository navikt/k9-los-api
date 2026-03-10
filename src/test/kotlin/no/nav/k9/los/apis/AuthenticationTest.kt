package no.nav.k9.los.apis

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
import no.nav.k9.los.ktor.auth.*
import no.nav.k9.los.ktor.core.DefaultStatusPages
import no.nav.k9.los.ktor.core.logRequests
import no.nav.k9.los.testsupport.jws.Azure
import no.nav.k9.los.testsupport.jws.StaticJwkProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AuthenticationTest {

    private val jwkProvider = StaticJwkProvider(Azure.V2_0.getPublicJwk())

    @Test
    fun `POST request med og uten CORS`() {
        testApplication {
            application {
                testApp(cors = true)
            }
            sendJsonRequest(forventetHttpResponseCode = HttpStatusCode.Forbidden)
        }

        testApplication {
            application {
                testApp(cors = false)
            }
            sendJsonRequest(forventetHttpResponseCode = HttpStatusCode.NoContent)
            sendJsonRequest(
                forventetHttpResponseCode = HttpStatusCode.Forbidden, authorizationHeader = authorizationHeader(
                    audience = "feil-audience"
                )
            )
        }
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

    private fun Application.testApp(cors: Boolean) {
        install(CallLogging) {
            logRequests()
        }
        install(StatusPages) {
            AuthStatusPages()
            DefaultStatusPages()
        }

        val azureV2 = Issuer(
            issuer = Azure.V2_0.getIssuer(),
            audience = "k9-los-api",
            alias = "azure-v2",
            jwkProvider = jwkProvider
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