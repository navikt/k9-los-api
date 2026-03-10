package no.nav.k9.los.ktor.core

import io.ktor.http.*
import io.ktor.server.plugins.calllogging.CallLoggingConfig
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

private val LOG: Logger = LoggerFactory.getLogger("no.nav.k9.los.ktor.core.CallLoggingExt")

fun CallLoggingConfig.correlationIdAndRequestIdInMdc() {
    callIdMdc("correlation_id")
    mdc("request_id") { call ->
        val requestId = when (val fraHeader = call.request.header(HttpHeaders.XRequestId)?.removePrefix("generated-")) {
            null -> "generated-${java.util.UUID.randomUUID()}"
            else -> when (IdVerifier.verifyId(type = HttpHeaders.XRequestId, id = fraHeader)) {
                true -> fraHeader
                false -> "generated-${java.util.UUID.randomUUID()}"
            }
        }
        requestId
    }
}

fun CallLoggingConfig.logRequests(
    excludePaths : Set<String> = Paths.DEFAULT_EXCLUDED_PATHS,
) {
    logger = LOG
    level = Level.INFO
    filter { call -> !excludePaths.contains(call.request.path()) }
    disableDefaultColors()
}

fun ApplicationRequest.log(
        verbose : Boolean = false,
        excludePaths : Set<String> = Paths.DEFAULT_EXCLUDED_PATHS,
) {
    if (!excludePaths.contains(call.request.path())) {
        LOG.info("Request ${httpMethod.value} $uri (HTTP Version $httpVersion)")
        if (verbose) {
            LOG.info("Origin ${header(HttpHeaders.Origin)} (User Agent ${userAgent()})")
        }
    }
}
