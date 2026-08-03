package no.nav.k9.los.oppgavedefinisjon.feltdefinisjon

data class FeltdefinisjonerDto(
    val område: String,
    val feltdefinisjoner: Set<FeltdefinisjonDto>
)
