package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.infrastruktur.abac.Gruppeoppsett
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.UUID

internal object TestKontekstFactory {
    fun brukerkontekst(
        område: Områder,
        idToken: IdToken = IdTokenLocal(),
        navIdent: String = idToken.getNavIdent(),
        grupper: Set<UUID> = idToken.groups.mapTo(mutableSetOf(), UUID::fromString),
        gruppeoppsett: Gruppeoppsett = Gruppeoppsett(),
        lokaleTilganger: Boolean = true,
    ) = Brukerkontekstfabrikk(gruppeoppsett, lokaleTilganger)
        .medOmråde(område, InnloggetBruker(navIdent, grupper, idToken))

    fun brukerkontekstUtenOmråde(
        idToken: IdToken = IdTokenLocal(),
        navIdent: String = idToken.getNavIdent(),
        grupper: Set<UUID> = idToken.groups.mapTo(mutableSetOf(), UUID::fromString),
        gruppeoppsett: Gruppeoppsett = Gruppeoppsett(),
        lokaleTilganger: Boolean = true,
    ) = Brukerkontekstfabrikk(gruppeoppsett, lokaleTilganger)
        .utenOmråde(InnloggetBruker(navIdent, grupper, idToken))
}
