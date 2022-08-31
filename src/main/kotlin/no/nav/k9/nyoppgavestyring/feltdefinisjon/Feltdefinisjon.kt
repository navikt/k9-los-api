package no.nav.k9.nyoppgavestyring.feltdefinisjon

import no.nav.k9.nyoppgavestyring.omraade.Område


class Feltdefinisjon(
    val id: Long? = null,
    val eksternId: String,
    val område: Område,
    val listetype: Boolean,
    val tolkesSom: String,
    val visTilBruker: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as no.nav.k9.nyoppgavestyring.feltdefinisjon.Feltdefinisjon

        if (eksternId != other.eksternId) return false
        if (listetype != other.listetype) return false
        if (tolkesSom != other.tolkesSom) return false
        if (visTilBruker != other.visTilBruker) return false

        return true
    }

    override fun hashCode(): Int {
        var result = eksternId.hashCode()
        result = 31 * result + listetype.hashCode()
        result = 31 * result + tolkesSom.hashCode()
        result = 31 * result + visTilBruker.hashCode()
        return result
    }
}