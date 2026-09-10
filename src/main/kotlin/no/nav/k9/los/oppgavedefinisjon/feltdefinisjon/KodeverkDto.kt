package no.nav.k9.los.oppgavedefinisjon.feltdefinisjon

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class KodeverkDto(
    val område: Områder,
    val eksternId: String,
    val beskrivelse: String? = null,
    val uttømmende: Boolean,
    val verdier: List<KodeverkVerdiDto>
)
