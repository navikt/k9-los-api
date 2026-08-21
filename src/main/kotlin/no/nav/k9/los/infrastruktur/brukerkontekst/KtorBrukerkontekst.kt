package no.nav.k9.los.infrastruktur.brukerkontekst

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenAzure
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.områdeAttributeKey
import org.koin.ktor.ext.getKoin

suspend fun <T> RoutingContext.medBrukerkontekst(
    block: suspend (BrukerkontekstMedOmråde) -> T,
): T {
    val område = call.attributes.getOrNull(områdeAttributeKey)
        ?: throw IllegalStateException("Endepunktet er ikke registrert under en områdeApi-rute")
    val brukerkontekstMedOmråde = call.application.getKoin().get<BrukerkontekstFactory>().medOmråde(område, idToken())
    return withContext(Span.current().asContextElement()) {
        block(brukerkontekstMedOmråde)
    }
}

suspend fun <T> RoutingContext.medBrukerkontekstUtenOmråde(
    block: suspend (BrukerkontekstUtenOmråde) -> T,
): T {
    val brukerkontekstUtenOmråde = call.application.getKoin().get<BrukerkontekstFactory>().utenOmråde(idToken())
    return withContext(Span.current().asContextElement()) {
        block(brukerkontekstUtenOmråde)
    }
}

private fun RoutingContext.idToken(): IdToken {
    val principal = call.principal<JWTPrincipal>()
    return if (principal != null) {
        val authorizationHeader = call.request.parseAuthorizationHeader()?.render()
            ?: throw IllegalStateException("Token ikke satt")
        val jwt = authorizationHeader.substringAfter("Bearer ")
        IdTokenAzure.fra(jwt, principal)
    } else {
        val profile = call.application.getKoin().getOrNull<KoinProfile>()
        check(profile == KoinProfile.LOCAL) { "Validert principal ikke satt" }
        IdTokenLocal()
    }
}
