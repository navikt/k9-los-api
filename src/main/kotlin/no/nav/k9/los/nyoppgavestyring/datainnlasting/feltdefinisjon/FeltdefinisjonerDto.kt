package no.nav.k9.los.nyoppgavestyring.datainnlasting.feltdefinisjon

data class FeltdefinisjonerDto(
    val område: String,
    val feltdefinisjoner: Set<FeltdefinisjonDto>
)
