package no.nav.k9.nyoppgavestyring.feltdefinisjon

data class FeltdefinisjonerDto(
    val område: String,
    val feltdefinisjoner: Set<FeltdefinisjonDto>
)
