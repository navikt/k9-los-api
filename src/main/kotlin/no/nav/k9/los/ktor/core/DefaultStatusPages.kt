package no.nav.k9.los.ktor.core

import io.ktor.http.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("no.nav.k9.los.ktor.core.DefaultStatusPages")

fun StatusPagesConfig.DefaultStatusPages() {
    exception<Throwable> { call, cause ->
        logger.warn("Uhåndtert feil", cause)
        call.respondText(
            text = "En uhåndtert feil har oppstått.",
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.InternalServerError
        )
    }
}
