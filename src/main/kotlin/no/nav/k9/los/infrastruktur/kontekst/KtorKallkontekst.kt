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
import no.nav.k9.los.område
import org.koin.ktor.ext.getKoin
import java.util.UUID

suspend fun <T> RoutingContext.medBrukerkontekst(
    block: suspend (Brukerkontekst) -> T,
): T = medInnloggetBruker { bruker ->
    val kontekst = Brukerkontekst(område = call.område, bruker = bruker)
    block(kontekst)
}

suspend fun <T> RoutingContext.medInnloggetBruker(
    block: suspend (InnloggetBruker) -> T,
): T = withContext(Span.current().asContextElement()) {
    block(innloggetBruker())
}

private fun RoutingContext.innloggetBruker(): InnloggetBruker {
    val idToken = if (call.application.getKoin().get<KoinProfile>() == KoinProfile.LOCAL) {
        IdTokenLocal()
    } else {
        val authorizationHeader = call.request.parseAuthorizationHeader()?.render()
            ?: throw IllegalStateException("Token ikke satt")
        val jwt = authorizationHeader.substringAfter("Bearer ")
        val principal = call.principal<JWTPrincipal>()
            ?: throw IllegalStateException("Validert principal ikke satt")
        IdTokenAzure.fra(jwt, principal)
    }

    return InnloggetBruker(
        navIdent = idToken.getNavIdent(),
        grupper = idToken.groups.mapTo(mutableSetOf(), UUID::fromString),
        idToken = idToken,
    )
}
