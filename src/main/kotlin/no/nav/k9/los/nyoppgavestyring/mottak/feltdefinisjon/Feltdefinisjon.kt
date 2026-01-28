package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.spi.felter.TransientFeltutleder


class Feltdefinisjon(
    val id: Long? = null,
    val eksternId: String,
    val område: Område,
    val visningsnavn: String,
    val beskrivelse: String?,
    val listetype: Boolean,
    val tolkesSom: String,
    val visTilBruker: Boolean,
    val kokriterie: Boolean,
    val kodeverkreferanse: Kodeverkreferanse?,
    val transientFeltutleder: TransientFeltutleder?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Feltdefinisjon

        if (eksternId != other.eksternId) return false
        if (område != other.område) return false
        if (visningsnavn != other.visningsnavn) return false
        if (beskrivelse != other.beskrivelse) return false
        if (listetype != other.listetype) return false
        if (tolkesSom != other.tolkesSom) return false
        if (visTilBruker != other.visTilBruker) return false
        if (kokriterie != other.kokriterie) return false
        if (kodeverkreferanse != other.kodeverkreferanse) return false
        if (transientFeltutleder != other.transientFeltutleder) return false

        return true
    }

    override fun hashCode(): Int {
        var result = eksternId.hashCode()
        result = 31 * result + område.hashCode()
        result = 31 * result + visningsnavn.hashCode()
        result = 31 * result + (beskrivelse?.hashCode() ?: 0)
        result = 31 * result + listetype.hashCode()
        result = 31 * result + tolkesSom.hashCode()
        result = 31 * result + visTilBruker.hashCode()
        result = 31 * result + kokriterie.hashCode()
        result = 31 * result + kodeverkreferanse.hashCode()
        return result
    }

    override fun toString(): String {
        return "Feltdefinisjon: eksternId $eksternId område ${område.eksternId}"
    }
}