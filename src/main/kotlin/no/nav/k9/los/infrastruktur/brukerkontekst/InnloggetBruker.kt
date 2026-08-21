package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.infrastruktur.idtoken.IdToken
import java.util.*

data class InnloggetBruker(
    val navIdent: String,
    val grupper: Set<UUID>,
    val idToken: IdToken,
)