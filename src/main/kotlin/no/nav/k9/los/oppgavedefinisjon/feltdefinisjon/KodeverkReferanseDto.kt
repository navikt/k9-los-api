package no.nav.k9.los.oppgavedefinisjon.feltdefinisjon

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class KodeverkReferanseDto(
    val område: Områder,
    val eksternId: String
)
