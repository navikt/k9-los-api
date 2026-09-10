package no.nav.k9.los.oppgavedefinisjon.feltdefinisjon

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class FeltdefinisjonerDto(
    val område: Områder,
    val feltdefinisjoner: Set<FeltdefinisjonDto>
)
