package no.nav.k9.los.nyoppgavestyring.datainnlasting.feltdefinisjon

data class KodeverkDto(
    val område: String,
    val eksternId: String,
    val beskrivelse: String? = null,
    val uttømmende: Boolean,
    val verdier: List<KodeverkVerdiDto>
)