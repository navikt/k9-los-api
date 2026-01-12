package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

class Kodeverkverdi(
    val id: Long? = null,
    val verdi: String,
    val visningsnavn: String,
    val beskrivelse: String?,
    val favoritt: Boolean,
    val gruppering: String?
) {
    constructor(kodeverkVerdiDto: KodeverkVerdiDto) :this (
        verdi = kodeverkVerdiDto.verdi,
        visningsnavn = kodeverkVerdiDto.visningsnavn,
        beskrivelse = kodeverkVerdiDto.beskrivelse,
        favoritt = kodeverkVerdiDto.favoritt,
        gruppering = kodeverkVerdiDto.gruppering
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Kodeverkverdi

        if (verdi != other.verdi) return false
        if (visningsnavn != other.visningsnavn) return false
        return beskrivelse == other.beskrivelse
    }

    override fun hashCode(): Int {
        var result = verdi.hashCode()
        result = 31 * result + visningsnavn.hashCode()
        result = 31 * result + (beskrivelse?.hashCode() ?: 0)
        return result
    }
}
