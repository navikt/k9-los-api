package no.nav.k9.los.tjenester.konfig

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import org.koin.ktor.ext.inject

fun Route.KonfigApis() {
    val configuration by inject<Configuration>()
    val k9sakUrlDev = "https://k9.dev.intern.nav.no/k9/web"
    val k9sakUrlProd = "https://k9.intern.nav.no/k9/web"
    val refreshUrlDev = "wss://k9-los-oidc-auth-proxy.dev.intern.nav.no/ws/k9-los-api"
    val refreshUrlProd = "wss://k9-los-oidc-auth-proxy.intern.nav.no/ws/k9-los-api"
    val refreshUrlLocal = "ws://localhost:8020/ws"
    val k9punsjUrlDev = "https://k9-punsj-frontend.intern.dev.nav.no/journalpost"
    val k9punsjUrlProd = "https://k9-punsj-frontend.intern.nav.no/journalpost"

    get("/k9-sak-url") {
        if (KoinProfile.PREPROD == configuration.koinProfile()) call.respond(Konfig(k9sakUrlDev)) else call.respond(
            Konfig(k9sakUrlProd)
        )
    }

    get("/k9-punsj-url") {
        if (KoinProfile.PREPROD == configuration.koinProfile()) call.respond(Konfig(k9punsjUrlDev)) else call.respond(
            Konfig(k9punsjUrlProd)
        )
    }

    get("/refresh-url") {
        when {
            configuration.koinProfile() == KoinProfile.PROD -> {
                call.respond(Konfig(refreshUrlProd))
            }

            KoinProfile.PREPROD == configuration.koinProfile() -> {
                call.respond(Konfig(refreshUrlDev))
            }

            else -> {
                call.respond(Konfig(refreshUrlLocal))
            }
        }
    }
}

class Konfig(val verdi: String)
