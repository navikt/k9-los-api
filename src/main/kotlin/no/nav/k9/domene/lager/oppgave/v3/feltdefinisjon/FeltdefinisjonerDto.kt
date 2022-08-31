package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

data class FeltdefinisjonerDto(
    val område: String,
    val feltdefinisjoner: Set<FeltdefinisjonDto>
)
