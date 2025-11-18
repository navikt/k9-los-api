package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

data class KodeverkVerdiDto(
    val verdi: String,
    val visningsnavn: String,
    val favoritt: Boolean = false,
    val beskrivelse: String? = null,
) {
    constructor(kodeverkverdi: Kodeverkverdi) : this(
        verdi = kodeverkverdi.visningsnavn,
        visningsnavn = kodeverkverdi.visningsnavn,
        favoritt = kodeverkverdi.favoritt,
        beskrivelse = kodeverkverdi.beskrivelse,
    )
}
