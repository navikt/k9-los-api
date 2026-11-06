package no.nav.k9.los.tjenester.mock

import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.server.request.contentType
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.k9.los.KoinProfile
import java.util.UUID

internal fun Route.testdataApi(
    profile: KoinProfile,
    enabled: Boolean,
    clusterName: String? = null,
    tjeneste: () -> TestdataTjeneste,
) {
    if (profile != KoinProfile.LOCAL || !enabled || !clusterName.isNullOrBlank()) return

    val testdata by lazy(tjeneste)
    route("/api/testdata") {
        get("/ready") {
            call.respond(if (testdata.erKlar()) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable)
        }
        post("/oppgaver") {
            // HTML-skjemaer fra andre nettsteder skal ikke kunne opprette lokale testdata.
            if (!call.request.contentType().match(ContentType.Application.Json)) {
                call.respond(HttpStatusCode.UnsupportedMediaType)
            } else {
                call.respond(HttpStatusCode.Created, testdata.opprettOppgave())
            }
        }
        delete("/oppgaver/{id}") {
            val id = runCatching { UUID.fromString(call.parameters["id"]) }.getOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
            } else if (testdata.slettOppgave(id)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
