package no.nav.k9.los.infrastruktur.rest

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.http.parametersOf
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

val områdeAttributeKey = AttributeKey<Områder>("los-omrade")

enum class OmrådeUrlSegment {
    k9,
    akt,
}

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
    createChild(OmrådeRouteSelector).apply {
        medOmrådePlugin { call ->
            Områder.fraUrlSegment(checkNotNull(call.parameters["omrade"]))
        }
        build()
    }

fun Route.områdeApi(område: Områder, build: Route.() -> Unit): Route =
    route("") {
        medOmrådePlugin { område }
        build()
    }

private object OmrådeRouteSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val urlSegment = context.segments.getOrNull(segmentIndex) ?: return RouteSelectorEvaluation.FailedPath
        return if (Områder.entries.any { it.urlSegment == urlSegment }) {
            RouteSelectorEvaluation.Success(
                RouteSelectorEvaluation.qualityConstant,
                parametersOf("omrade", urlSegment),
                segmentIncrement = 1,
            )
        } else {
            RouteSelectorEvaluation.FailedPath
        }
    }

    override fun toString(): String = "{omrade}"
}
