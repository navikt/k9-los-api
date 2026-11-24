package no.nav.k9.los.tjenester.mock

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.*
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
            val slettet = testdataTjeneste.slettOppgave(id)
            call.respond(if (slettet) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
    }
}
