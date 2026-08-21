package no.nav.k9.los.infrastruktur.brukerkontekst

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

@ConsistentCopyVisibility // For å ikke omgå konstruktørens synlighet via copy-metode
data class BrukerkontekstMedOmråde internal constructor(
    val område: Områder,
    val bruker: InnloggetBruker,
)

@ConsistentCopyVisibility
data class BrukerkontekstUtenOmråde internal constructor(
    val bruker: InnloggetBruker,
)