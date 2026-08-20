package no.nav.k9.los.infrastruktur.kontekst

import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.abac.Gruppeoppsett
import no.nav.k9.los.infrastruktur.idtoken.IdTokenAzure
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.områdeAttributeKey
import no.nav.k9.los.reservasjon.ManglerTilgangException
import org.koin.ktor.ext.getKoin
import java.util.*

suspend fun <T> RoutingContext.medBrukerkontekst(
    block: suspend (Brukerkontekst) -> T,
): T {
    val område = call.attributes.getOrNull(områdeAttributeKey)
        ?: throw IllegalStateException("Endepunktet er ikke registrert under en områdeApi-rute")
    val kontekst = Brukerkontekst(område, innloggetBruker())
    return withContext(Span.current().asContextElement()) {
        block(kontekst)
    }
}

suspend fun <T> RoutingContext.medBrukerkontekstUtenOmråde(
    block: suspend (BrukerkontekstUtenOmråde) -> T,
): T {
    val kontekst = BrukerkontekstUtenOmråde(innloggetBruker())
    return withContext(Span.current().asContextElement()) {
        block(kontekst)
    }
}

internal fun systemkontekst(område: no.nav.k9.los.oppgavedefinisjon.omraade.Områder) = Systemkontekst(område)

internal fun systemkontekstUtenOmråde() = SystemkontekstUtenOmråde()

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
