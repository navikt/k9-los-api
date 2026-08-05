package no.nav.k9.los.saksbehandleradmin

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class EpostOgOmraadeDto(
    val epost: String,
    val omraade: Områder
)

