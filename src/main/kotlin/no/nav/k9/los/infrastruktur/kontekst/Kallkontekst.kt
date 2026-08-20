package no.nav.k9.los.infrastruktur.kontekst

import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import java.util.UUID

data class InnloggetBruker(
    val navIdent: String,
    val grupper: Set<UUID>,
    val idToken: IdToken,
)

sealed interface Kallkontekst

sealed interface Brukerkall : Kallkontekst {
    val bruker: InnloggetBruker
}

sealed interface Systemkall : Kallkontekst

sealed interface Områdekall : Kallkontekst {
    val område: Områder
}

data class Brukerkontekst(
    override val bruker: InnloggetBruker,
) : Brukerkall

data class Områdebrukerkontekst(
    override val område: Områder,
    override val bruker: InnloggetBruker,
) : Brukerkall, Områdekall

data object Systemkontekst : Systemkall

data class Områdesystemkontekst(
    override val område: Områder,
) : Systemkall, Områdekall
