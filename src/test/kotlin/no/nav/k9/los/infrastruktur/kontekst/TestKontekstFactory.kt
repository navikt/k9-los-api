package no.nav.k9.los.infrastruktur.kontekst

import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.UUID

object TestKontekstFactory {
    fun brukerkontekst(
        område: Områder,
        idToken: IdToken = IdTokenLocal(),
        navIdent: String = idToken.getNavIdent(),
        grupper: Set<UUID> = idToken.groups.mapTo(mutableSetOf(), UUID::fromString),
    ) = BrukerkontekstMedOmråde(område, InnloggetBruker(navIdent, grupper, idToken))

    fun systemkontekst(område: Områder) = Systemkontekst(område)

    fun brukerkontekstUtenOmråde(
        idToken: IdToken = IdTokenLocal(),
        navIdent: String = idToken.getNavIdent(),
        grupper: Set<UUID> = idToken.groups.mapTo(mutableSetOf(), UUID::fromString),
    ) = BrukerkontekstUtenOmråde(InnloggetBruker(navIdent, grupper, idToken))

    fun systemkontekstUtenOmråde() = SystemkontekstUtenOmråde
}
