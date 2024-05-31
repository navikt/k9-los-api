package no.nav.k9.los.nyoppgavestyring.datainnlasting.feltdefinisjon

data class KodeverkVerdiDto(
    val verdi: String,
    val visningsnavn: String,
    val favoritt: Boolean = false,
    val beskrivelse: String? = null,
)
