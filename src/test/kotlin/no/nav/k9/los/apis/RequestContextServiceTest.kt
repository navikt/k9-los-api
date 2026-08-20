package no.nav.k9.los.apis

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.testsupport.jws.Azure
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.dusseldorf.testsupport.wiremock.getAzureV2JwksUrl
import no.nav.k9.los.infrastruktur.abac.PepClient
import no.nav.k9.los.infrastruktur.abac.OmrådeIkkeTilgjengeligException
import no.nav.k9.los.infrastruktur.abac.SifAbacPdpKlienter
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.kontekst.medBrukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.medInnloggetBruker
import no.nav.k9.los.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import io.mockk.mockk

internal class KallkonteksmTest {

    @Test
    fun `bruker claims fra validert principal for dynamisk og fast område`() = medTestApp { client ->
        val authorization = authorizationHeader(username = "Erik")

        val dynamisk = client.get("/k9/med-request-context") { header(HttpHeaders.Authorization, authorization) }
        assertEquals(HttpStatusCode.OK, dynamisk.status)
        assertEquals("Hei Erik fra K9", dynamisk.bodyAsText())

        val legacy = client.get("/legacy") { header(HttpHeaders.Authorization, authorization) }
        assertEquals(HttpStatusCode.OK, legacy.status)
        assertEquals("Hei Erik fra K9", legacy.bodyAsText())
    }

    @Test
    fun `områdeuavhengig rute kan etablere identitet`() = medTestApp { client ->
        val response = client.get("/uten-område") {
            header(HttpHeaders.Authorization, authorizationHeader(username = "Erik"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hei Erik", response.bodyAsText())
    }

    @Test
    fun `manglende token gir 401 og UNG uten implementert tilgang gir 403`() = medTestApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/k9/med-request-context").status)

        val response = client.get("/ung/tilgang") {
            header(HttpHeaders.Authorization, authorizationHeader())
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    private fun medTestApp(test: suspend (io.ktor.client.HttpClient) -> Unit) {
        val wireMock = WireMockBuilder().withAzureSupport().build()
        try {
            testApplication {
                application { testApp(wireMock) }
                test(client)
            }
        } finally {
            wireMock.stop()
        }
    }

    private fun Application.testApp(wireMock: WireMockServer) {
        val issuer = Issuer(
            issuer = Azure.V2_0.getIssuer(),
            jwksUri = URI(wireMock.getAzureV2JwksUrl()),
            audience = "k9-los-api",
            alias = "azure-v2",
        )
        val issuers = mapOf(issuer.alias() to issuer).withoutAdditionalClaimRules()
        install(Authentication) { multipleJwtIssuers(issuers) }
        install(StatusPages) {
            AuthStatusPages()
            exception<OmrådeIkkeTilgjengeligException> { call, _ ->
                call.respond(HttpStatusCode.Forbidden)
            }
        }

        val pepClient = PepClient(mockk<IAzureGraphService>(), mockk<SifAbacPdpKlienter>())

        routing {
            authenticate(*issuers.allIssuers()) {
                områdeApi {
                    get("med-request-context") {
                        medBrukerkontekst { kontekst ->
                            call.respondText("Hei ${kontekst.bruker.idToken.getUsername()} fra ${kontekst.område}")
                        }
                    }
                    get("tilgang") {
                        medBrukerkontekst { kontekst ->
                            pepClient.diskresjonskoderForSak("sak", kontekst)
                            call.respond(HttpStatusCode.OK)
                        }
                    }
                }
                områdeApi(Områder.K9) {
                    get("legacy") {
                        medBrukerkontekst { kontekst ->
                            call.respondText("Hei ${kontekst.bruker.idToken.getUsername()} fra ${kontekst.område}")
                        }
                    }
                }
                get("uten-område") {
                    medInnloggetBruker { kontekst ->
                        call.respondText("Hei ${kontekst.bruker.idToken.getUsername()}")
                    }
                }
            }
        }
    }
}