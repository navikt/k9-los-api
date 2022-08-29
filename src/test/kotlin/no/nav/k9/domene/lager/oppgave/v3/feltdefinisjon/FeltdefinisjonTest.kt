package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.*
import io.ktor.server.testing.*
import no.nav.k9.AbstractK9LosIntegrationTest
import no.nav.k9.domene.lager.oppgave.v3.omraade.OmrådeRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import kotlin.test.*

class FeltdefinisjonTest : AbstractK9LosIntegrationTest() {

    lateinit var områdeRepository: OmrådeRepository

    @BeforeEach
    fun setup() {
        områdeRepository = get()
        områdeRepository.lagre("K9")
    }

    @Test
    fun `test`() {
        /*val response = client.post("/api/feltdefinisjon") {
            contentType(ContentType.Application.Json)
            setBody()
        }
        val feltdefinisjonRepository = get<FeltdefinisjonRepository>()
        //val response = client.post("/api/feltdefinisjon")

         */
    }

    private fun TestApplicationEngine.sendJsonRequest(
        forventetHttpResponseCode: HttpStatusCode
    ) {
        handleRequest(HttpMethod.Post, "api/feltdefinisjon") {
            addHeader(HttpHeaders.ContentType, "application/json")
            addHeader(HttpHeaders.Origin, "https://k9-los.nav.no")
            setBody(
                """
                {
                  "område": "K9",
                  "feltdefinisjoner": [
                    {
                      "id": "saksnummer",
                      "listetype": false,
                      "parsesSom": "String",
                      "visTilBruker": true
                    },
                    {
                      "id": "opprettet",
                      "listetype": false,
                      "parsesSom": "Date",
                      "visTilBruker": true
                    }
                  ]
                }
            """.trimIndent()
            )
        }.apply {
            assertEquals(forventetHttpResponseCode, response.status())
        }
    }

}