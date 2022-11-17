package no.nav.k9.los.integrasjon.rest

import io.ktor.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.tjenester.saksbehandler.IIdToken
import no.nav.k9.los.tjenester.saksbehandler.IdTokenLocal
import no.nav.k9.los.tjenester.saksbehandler.idToken
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

// For bruk i suspending functions
// https://blog.tpersson.io/2018/04/22/emulating-request-scoped-objects-with-kotlin-coroutines/
private class CoroutineRequestContext(
    val idToken: IIdToken
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CoroutineRequestContext>
}

private fun CoroutineContext.requestContext() =
    get(CoroutineRequestContext) ?: throw IllegalStateException("Request Context ikke satt.")

internal fun CoroutineContext.idToken() = requestContext().idToken

internal class RequestContextService(
    private val profile: KoinProfile
)  {

    internal suspend fun <T>withRequestContext(call: ApplicationCall, block: suspend CoroutineScope.() -> T) = withContext(
        context = establish(call),
        block = block
    )

    private suspend fun establish(call: ApplicationCall) = coroutineContext + CoroutineRequestContext(
        idToken = when (profile == KoinProfile.LOCAL) {
            true -> IdTokenLocal()
            false -> call.idToken()
        }
    )
}