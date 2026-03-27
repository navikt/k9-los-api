package no.nav.k9.los.ktor.metrics

import io.ktor.http.*
import io.ktor.server.metrics.micrometer.*

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
    }
}

private fun HttpStatusCode.isSuccessOrRedirect() = value in (200 until 400)
