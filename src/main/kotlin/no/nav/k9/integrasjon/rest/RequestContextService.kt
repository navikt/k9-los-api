package no.nav.k9.integrasjon.rest

import io.ktor.application.*
import kotlinx.coroutines.withContext
import no.nav.k9.KoinProfile
import no.nav.k9.tjenester.saksbehandler.IIdToken
import no.nav.k9.tjenester.saksbehandler.IdTokenLocal
import no.nav.k9.tjenester.saksbehandler.idToken
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
    get(CoroutineRequestContext.Key) ?: throw IllegalStateException("Request Context ikke satt.")

internal fun CoroutineContext.idToken() = requestContext().idToken

class RequestContextService(
    private val profile: KoinProfile)  {

    suspend fun withRequestContext(call: ApplicationCall, block : suspend() -> Unit) = withContext(establish(call)) {
        block()
    }

    private suspend fun establish(call: ApplicationCall): CoroutineContext {
        return coroutineContext + CoroutineRequestContext(
            idToken = when (profile == KoinProfile.LOCAL) {
                true -> IdTokenLocal()
                false -> call.idToken()
            }
        )
    }
}