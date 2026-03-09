package no.nav.k9.los.ktor.metrics

import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.util.*

fun MicrometerMetricsConfig.init(app: String) {
    timers { call, throwable ->
        tag("app", app)
        tag("result",
                when {
                    throwable != null -> "failure"
                    call.response.status() == null -> "failure"
                    call.response.status()!!.isSuccessOrRedirect() -> "success"
                    else -> "failure"
                }
        )
        when(call.response.status()) {
            Unauthorized, Forbidden, NotFound -> {}
            else -> {
                tag("problem_details", call.resolveProblemDetailsTag())
            }
        }
    }
}

private fun ApplicationCall.resolveProblemDetailsTag(): String =
        when (val problemDetailsKey = attributes.allKeys.firstOrNull { it.name == "problem-details" }) {
            null -> "n/a"
            else -> {
                @Suppress("UNCHECKED_CAST")
                attributes[problemDetailsKey as AttributeKey<String>]
            }
        }

private fun HttpStatusCode.isSuccessOrRedirect() = value in (200 until 400)
