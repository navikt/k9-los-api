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
    sealed interface MedBruker : Kallkontekst {
        val bruker: InnloggetBruker
    }

    sealed interface MedOmråde : Kallkontekst {
        val område: Områder
    }
}

@ConsistentCopyVisibility
data class Brukerkontekst internal constructor(
    override val område: Områder,
    override val bruker: InnloggetBruker,
) : Kallkontekst.MedBruker, Kallkontekst.MedOmråde

@ConsistentCopyVisibility
data class Systemkontekst internal constructor(
    override val område: Områder,
) : Kallkontekst.MedOmråde

@ConsistentCopyVisibility
data class BrukerkontekstUtenOmråde internal constructor(
    override val bruker: InnloggetBruker,
) : Kallkontekst.MedBruker

class SystemkontekstUtenOmråde internal constructor() : Kallkontekst {
    // equals og hashCode ihht. identitet, implementert for å unngå warning
    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }
}
