package no.nav.k9.los.infrastruktur.rest

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenK9
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.infrastruktur.idtoken.IdTokenUng
import no.nav.k9.los.område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

// For bruk i suspending functions
// https://blog.tpersson.io/2018/04/22/emulating-request-scoped-objects-with-kotlin-coroutines/
public class CoroutineRequestContext(
    val idToken: IdToken
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CoroutineRequestContext>
}

private fun CoroutineContext.requestContext() =
    get(CoroutineRequestContext) ?: throw IllegalStateException("Request Context ikke satt.")

internal fun CoroutineContext.idToken() = requestContext().idToken

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
            when (call.område) {
                Områder.K9 -> IdTokenK9(jwt)
                Områder.UNG -> IdTokenUng(jwt)
            }
        }
    )
}