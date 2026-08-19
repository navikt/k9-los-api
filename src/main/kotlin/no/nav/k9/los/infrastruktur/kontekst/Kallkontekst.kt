package no.nav.k9.los.infrastruktur.kontekst

import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.UUID

data class InnloggetBruker(
    val navIdent: String,
    val grupper: Set<UUID>,
    val idToken: IdToken,
)

sealed interface Kallkontekst {
    val område: Områder
}

data class Brukerkontekst(
    override val område: Områder,
    val bruker: InnloggetBruker,
) : Kallkontekst

data class Systemkontekst(
    override val område: Områder,
    val kilde: String,
) : Kallkontekst
