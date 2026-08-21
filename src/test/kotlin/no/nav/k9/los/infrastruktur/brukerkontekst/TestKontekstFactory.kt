package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.abac.Gruppeoppsett
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.*

internal object TestKontekstFactory {
    fun brukerkontekst(
        område: Områder,
        idToken: IdToken = IdTokenLocal(),
        gruppeoppsett: Gruppeoppsett = Gruppeoppsett(),
        lokaleTilganger: Boolean = true,
    ) = BrukerkontekstFactory(gruppeoppsett, lokaleTilganger)
        .medOmråde(område, idToken)

    fun brukerkontekstUtenOmråde(
        idToken: IdToken = IdTokenLocal(),
        navIdent: String = idToken.getNavIdent(),
        grupper: Set<UUID> = idToken.groups.mapTo(mutableSetOf(), UUID::fromString),
        gruppeoppsett: Gruppeoppsett = Gruppeoppsett(),
        lokaleTilganger: Boolean = true,
    ) = BrukerkontekstFactory(gruppeoppsett, lokaleTilganger)
        .utenOmråde(idToken)
}
