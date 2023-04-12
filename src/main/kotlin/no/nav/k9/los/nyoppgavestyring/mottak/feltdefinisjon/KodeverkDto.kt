package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

data class KodeverkDto(
    val område: String,
    val eksternId: String,
    val beskrivelse: String,
    val verdier: List<KodeverkVerdiDto>
)