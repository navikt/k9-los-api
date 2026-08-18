package no.nav.k9.los

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

private val områdeAttributeKey = AttributeKey<Områder>("los-omrade")

private fun Route.medOmrådePlugin(områdeProvider: suspend (ApplicationCall) -> Områder?): Route = apply {
    install(
        createRouteScopedPlugin("OmrådeKontekst-${hashCode()}") {
            onCall { call ->
                områdeProvider(call)?.let { call.attributes.put(områdeAttributeKey, it) }
            }
        }
    )
}

/**
 * Registrerer et templatisert rotpunkt for område-API, f.eks. `k9/los/api/{omrade}/...`.
 *
 * Området leses fra path-parameteren `omrade` og legges på kallet slik at endepunkter under
 * ruten kan lese det via [ApplicationCall.område] uten å måtte tråkle parameteret gjennom alle route-funksjonene.
 */
fun Route.områdeApi(build: Route.() -> Unit): Route =
    route("{omrade}/") {
        medOmrådePlugin { call ->
            val urlSegment = call.parameters["omrade"]
            val område = try {
                urlSegment?.let(Områder::fraUrlSegment)
            } catch (_: IllegalArgumentException) {
                null
            }
            if (område == null) {
                call.respond(HttpStatusCode.NotFound)
                null
            } else {
                område
            }
        }
        build()
    }

fun Route.områdeApi(område: Områder, build: Route.() -> Unit): Route =
    route("") {
        medOmrådePlugin { område }
        build()
    }

/** Området ruten kallet traff ble registrert under. Se [områdeApi]. */
val ApplicationCall.område: Områder
    get() = områdeOrNull
        ?: throw IllegalStateException("Endepunktet er ikke registrert under en områdeApi-rute")

val ApplicationCall.områdeOrNull: Områder?
    get() = attributes.getOrNull(områdeAttributeKey)

val RoutingContext.område: Områder
    get() = call.område
