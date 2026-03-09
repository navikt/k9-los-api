package no.nav.k9.los.ktor.auth

import io.ktor.http.*
import io.ktor.http.ContentType
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respondText
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("no.nav.k9.los.ktor.auth.AuthStatusPages")

fun StatusPagesConfig.AuthStatusPages() {
    exception<ClaimEnforcementFailed> { call, cause ->
        logger.error("Request uten tilstrekkelig tilganger stoppet. Håndheving av regler resulterte i '${cause.outcomes}'")
        val json =
            """{"title":"${"unauthorized"}","status":${HttpStatusCode.Forbidden.value},"detail":"${"Requesten inneholder ikke tilstrekkelige tilganger."}"}"""
        if (null
            == null
        ) {
            logger.info("Error response='$json'")
        } else {
            logger.warn("Error response='$json'", p1 = null)
        }
        call.respondText(
            text = json,
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.Forbidden
        )
    }
}
