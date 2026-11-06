package no.nav.k9.los.tjenester.mock

import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.k9.los.KoinProfile
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class TestdataApiTest {
    @Test
    fun `testdata-endpoints finnes ikke i prod eller preprod selv med aktivering`() {
        listOf(KoinProfile.PROD, KoinProfile.PREPROD).forEach { profile ->
            testApplication {
                application {
                    routing { testdataApi(profile, enabled = true) { error("Skal ikke opprette tjenesten") } }
                }
                assertEquals(HttpStatusCode.NotFound, client.get("/api/testdata/ready").status)
                assertEquals(HttpStatusCode.NotFound, client.post("/api/testdata/oppgaver").status)
                assertEquals(HttpStatusCode.NotFound, client.delete("/api/testdata/oppgaver/${UUID.randomUUID()}").status)
            }
        }
    }

    @Test
    fun `testdata-endpoints er av som standard også lokalt`() = testApplication {
        application {
            routing { testdataApi(KoinProfile.LOCAL, enabled = false) { error("Skal ikke opprette tjenesten") } }
        }
        assertEquals(HttpStatusCode.NotFound, client.post("/api/testdata/oppgaver").status)
    }

    @Test
    fun `cluster-konfigurasjon stenger testdata også med feilaktig LOCAL-profil`() = testApplication {
        application {
            routing {
                testdataApi(KoinProfile.LOCAL, enabled = true, clusterName = "prod-fss") {
                    error("Skal ikke opprette tjenesten")
                }
            }
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/testdata/ready").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/testdata/oppgaver").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/testdata/oppgaver/${UUID.randomUUID()}").status)
    }

    @Test
    fun `ready gir 503 til lokale saksbehandlere er klare`() = testApplication {
        val tjeneste = mockk<TestdataTjeneste>()
        io.mockk.every { tjeneste.erKlar() } returnsMany listOf(false, true)
        application {
            routing { testdataApi(KoinProfile.LOCAL, enabled = true) { tjeneste } }
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/testdata/ready").status)
        assertEquals(HttpStatusCode.OK, client.get("/api/testdata/ready").status)
    }

    @Test
    fun `ugyldig id avvises før tjenesten opprettes`() = testApplication {
        application {
            routing { testdataApi(KoinProfile.LOCAL, enabled = true) { error("Skal ikke opprette tjenesten") } }
        }
        assertEquals(HttpStatusCode.BadRequest, client.delete("/api/testdata/oppgaver/ikke-en-uuid").status)
    }

    @Test
    fun `opprettelse avviser HTML-skjema før tjenesten opprettes`() = testApplication {
        application {
            routing { testdataApi(KoinProfile.LOCAL, enabled = true) { error("Skal ikke opprette tjenesten") } }
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, client.post("/api/testdata/oppgaver").status)
    }

    @Test
    fun `ukjent id gir 404 uten sletting`() = testApplication {
        val tjeneste = mockk<TestdataTjeneste>()
        val id = UUID.randomUUID()
        io.mockk.every { tjeneste.slettOppgave(id) } returns false
        application {
            routing { testdataApi(KoinProfile.LOCAL, enabled = true) { tjeneste } }
        }
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/testdata/oppgaver/$id").status)
    }
}
