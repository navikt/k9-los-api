package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

data class KodeverkVerdiDto(
    val verdi: String,
    val visningsnavn: String,
    val synlighet: Synlighet,
    val beskrivelse: String? = null,
    val gruppering: String? = null,
    val rekkefølge: Int? = null
)