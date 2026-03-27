package no.nav.k9.los.ktor.auth

import io.ktor.http.*
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respondText
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("no.nav.k9.los.ktor.auth.AuthStatusPages")

fun StatusPagesConfig.AuthStatusPages() {
    exception<ClaimEnforcementFailed> { call, cause ->
        logger.error("Request uten tilstrekkelig tilganger stoppet. Håndheving av regler resulterte i '${cause.outcomes}'")
        call.respondText(
            text = "Requesten inneholder ikke tilstrekkelige tilganger.",
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.Forbidden
        )
    }
}
