package no.nav.k9.los

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

private val områdeAttributeKey = AttributeKey<Områder>("los-omrade")

/**
 * Registrerer et rotpunkt for API-et til et [Områder], f.eks. `k9/los/api` eller `ung/los/api`.
 *
 * Området legges på kallet slik at endepunkter under ruten kan lese det via [ApplicationCall.område]
 * uten å måtte tråkle parameteret gjennom alle route-funksjonene.
 */
fun Route.områdeApi(område: Områder, build: Route.() -> Unit): Route =
    route("${område.urlSegment}/los/api") {
        install(
            createRouteScopedPlugin("OmrådeKontekst-${område.eksternId}") {
                onCall { call -> call.attributes.put(områdeAttributeKey, område) }
            }
        )
        build()
    }

/** Området ruten kallet traff ble registrert under. Se [områdeApi]. */
val ApplicationCall.område: Områder
    get() = attributes.getOrNull(områdeAttributeKey)
        ?: throw IllegalStateException("Endepunktet er ikke registrert under en områdeApi-rute")

val RoutingContext.område: Områder
    get() = call.område

