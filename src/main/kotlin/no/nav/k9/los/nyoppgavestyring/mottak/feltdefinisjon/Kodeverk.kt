package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område

class Kodeverk(
    val id: Long? = null,
    val område: Område,
    val eksternId: String,
    val beskrivelse: String?,
    val uttømmende: Boolean?,
    val verdier: List<Kodeverkverdi>,
) {
    constructor(kodeverkDto: KodeverkDto, område: Område) : this (
        område = område,
        eksternId = kodeverkDto.eksternId,
        beskrivelse = kodeverkDto.beskrivelse,
        uttømmende = kodeverkDto.uttømmende,
        verdier = kodeverkDto.verdier.map { dto ->
            Kodeverkverdi(dto)
        }
    )

    fun hentVerdi(verdi: String) : Kodeverkverdi? {
        return verdier.firstOrNull { kodeverkverdi ->
            kodeverkverdi.verdi == verdi
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Kodeverk

        if (område != other.område) return false
        if (eksternId != other.eksternId) return false
        if (beskrivelse != other.beskrivelse) return false
        if (uttømmende != other.uttømmende) return false
        return verdier == other.verdier
    }

    override fun hashCode(): Int {
        var result = område.hashCode()
        result = 31 * result + eksternId.hashCode()
        result = 31 * result + (beskrivelse?.hashCode() ?: 0)
        result = 31 * result + verdier.hashCode()
        return result
    }
}