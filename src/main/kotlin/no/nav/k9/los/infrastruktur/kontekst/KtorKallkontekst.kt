package no.nav.k9.los.infrastruktur.kontekst

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.application.*
import io.ktor.server.routing.RoutingContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.idtoken.IdTokenAzure
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.infrastruktur.rest.CoroutineRequestContext
import no.nav.k9.los.område
import org.koin.ktor.ext.getKoin
import java.util.UUID

suspend fun <T> RoutingContext.medBrukerkontekst(
    block: suspend (Områdebrukerkontekst) -> T,
): T = medInnloggetBruker { brukerkontekst ->
    val kontekst = Områdebrukerkontekst(område = call.område, bruker = brukerkontekst.bruker)
    block(kontekst)
}

suspend fun <T> RoutingContext.medInnloggetBruker(
    block: suspend (Brukerkontekst) -> T,
): T {
    val kontekst = Brukerkontekst(innloggetBruker())
    return withContext(CoroutineRequestContext(kontekst.bruker.idToken) + Span.current().asContextElement()) {
        block(kontekst)
    }
}

private fun RoutingContext.innloggetBruker(): InnloggetBruker {
    val principal = call.principal<JWTPrincipal>()
    val idToken = if (principal != null) {
        val authorizationHeader = call.request.parseAuthorizationHeader()?.render()
            ?: throw IllegalStateException("Token ikke satt")
        val jwt = authorizationHeader.substringAfter("Bearer ")
        IdTokenAzure.fra(jwt, principal)
    } else {
        val profile = call.application.getKoin().getOrNull<KoinProfile>()
        check(profile == KoinProfile.LOCAL) { "Validert principal ikke satt" }
        IdTokenLocal()
    }

    return InnloggetBruker(
        navIdent = idToken.getNavIdent(),
        grupper = idToken.groups.mapTo(mutableSetOf(), UUID::fromString),
        idToken = idToken,
    )
}
