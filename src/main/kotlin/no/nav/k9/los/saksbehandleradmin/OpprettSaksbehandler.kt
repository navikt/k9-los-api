package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class OpprettSaksbehandler(
    val områder: List<Områder>,
    val navident: String?,
    val navn: String?,
    val epost: String,
    val enhet: String?
)
