package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

data class KodeverkDto(
    val område: String,
    val eksternId: String,
    val beskrivelse: String? = null,
    val uttømmende: Boolean,
    val verdier: List<KodeverkVerdiDto>
)