package no.nav.k9.los.oppgavedefinisjon.feltdefinisjon

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class FeltdefinisjonerDto(
    val område: String,
    val feltdefinisjoner: Set<FeltdefinisjonDto>
) {
    constructor(område: Områder, feltdefinisjoner: Set<FeltdefinisjonDto>) : this(
        område = område.eksternId,
        feltdefinisjoner = feltdefinisjoner
    )
}
