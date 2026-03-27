package no.nav.k9.los.ktor.core

import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.server.plugins.callid.CallIdConfig
import io.ktor.server.request.header
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

internal object IdVerifier {
    private val logger: Logger = LoggerFactory.getLogger("no.nav.k9.los.ktor.core.IdVerifier")
    private const val NorskeBokstaver = "æøåÆØÅ"
    private const val GeneratedIdPrefix = "generated-"

    private val idRegex = "[a-zA-Z0-9_.\\-${NorskeBokstaver}]{5,200}".toRegex()
    internal fun verifyId(type: String, id: String) = idRegex.matches(id).also { valid ->
        if (!valid) logger.warn("Ugyldig $type=[${id.encodeURLParameter()}] (url-encoded)")
    }

    internal fun generate() = "$GeneratedIdPrefix${UUID.randomUUID()}"
}

fun CallIdConfig.fromXCorrelationIdHeader(generateOnInvalid: Boolean = false, generateOnNotSet: Boolean = false) {
    retrieve { call ->
        when (val fromHeader = call.request.header(HttpHeaders.XCorrelationId)) {
            null -> when (generateOnNotSet) {
                true -> IdVerifier.generate()
                false -> fromHeader
            }

            else -> when (IdVerifier.verifyId(type = HttpHeaders.XCorrelationId, id = fromHeader)) {
                true -> fromHeader
                false -> when (generateOnInvalid) {
                    true -> IdVerifier.generate()
                    false -> fromHeader
                }
            }
        }
    }

    verify { IdVerifier.verifyId(type = HttpHeaders.XCorrelationId, id = it) }
}

fun CallIdConfig.generated() {
    generate { IdVerifier.generate() }
}
