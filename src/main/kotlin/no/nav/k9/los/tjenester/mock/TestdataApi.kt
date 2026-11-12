package no.nav.k9.los.tjenester.mock

import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.server.request.contentType
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.k9.los.KoinProfile
import java.util.UUID

internal fun Route.testdataApi(
    testdataTjeneste: TestdataTjeneste,
) {
    route("/api/testdata") {
        get("/ready") {
            call.respond(if (testdataTjeneste.erKlar()) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable)
        }
        post("/oppgaver") {
            call.respond(HttpStatusCode.Created, testdataTjeneste.opprettOppgave())
        }
        delete("/oppgaver/{id}") {
            val id = UUID.fromString(call.parameters["id"])
            testdataTjeneste.slettOppgave(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
