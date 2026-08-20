package no.nav.k9.los.infrastruktur.kontekst

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

private sealed interface Kallkontekst

private sealed interface Brukererkontekst : Kallkontekst {
    val bruker: InnloggetBruker
}

private sealed interface Områdekontekst : Kallkontekst {
    val område: Områder
}

@ConsistentCopyVisibility
data class BrukerkontekstMedOmråde internal constructor(
    override val område: Områder,
    override val bruker: InnloggetBruker,
) : Brukererkontekst, Områdekontekst

@ConsistentCopyVisibility
data class Systemkontekst internal constructor(
    override val område: Områder,
) : Områdekontekst

@ConsistentCopyVisibility
data class BrukerkontekstUtenOmråde internal constructor(
    override val bruker: InnloggetBruker,
) : Brukererkontekst

data object SystemkontekstUtenOmråde : Kallkontekst