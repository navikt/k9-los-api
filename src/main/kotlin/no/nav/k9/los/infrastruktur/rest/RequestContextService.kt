package no.nav.k9.los.infrastruktur.rest

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenAzure
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.områdeOrNull
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

// For bruk i suspending functions
// https://blog.tpersson.io/2018/04/22/emulating-request-scoped-objects-with-kotlin-coroutines/
public class CoroutineRequestContext(
    val idToken: IdToken,
    val område: Områder?,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CoroutineRequestContext>
}

private fun CoroutineContext.requestContext() =
    get(CoroutineRequestContext) ?: throw IllegalStateException("Request Context ikke satt.")

internal fun CoroutineContext.idToken() = requestContext().idToken
internal fun CoroutineContext.område() = requestContext().område
    ?: throw IllegalStateException("Område er ikke satt i request context")

internal class RequestContextService(
    private val profile: KoinProfile
) {

    internal suspend fun <T> withRequestContext(call: ApplicationCall, block: suspend CoroutineScope.() -> T) =
        withContext(
            context = establish(call) + Span.current().asContextElement(),
            block = block
        )

    private suspend fun establish(call: ApplicationCall) = coroutineContext + CoroutineRequestContext(
        idToken = if (profile == KoinProfile.LOCAL) IdTokenLocal() else {
            val authorizationHeader = call.request.parseAuthorizationHeader()?.render() ?: throw IllegalStateException("Token ikke satt")
            val jwt = authorizationHeader.substringAfter("Bearer ")
            val principal = call.principal<JWTPrincipal>() ?: throw IllegalStateException("Validert principal ikke satt")
            IdTokenAzure.fra(jwt, principal)
        },
        område = call.områdeOrNull,
    )
}
