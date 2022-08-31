package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

class Feltdefinisjon(
    val id: Long? = null,
    val eksternId: String,
    val listetype: Boolean,
    val parsesSom: String,
    val visTilBruker: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Feltdefinisjon

        if (eksternId != other.eksternId) return false
        if (listetype != other.listetype) return false
        if (parsesSom != other.parsesSom) return false
        if (visTilBruker != other.visTilBruker) return false

        return true
    }

    override fun hashCode(): Int {
        var result = eksternId.hashCode()
        result = 31 * result + listetype.hashCode()
        result = 31 * result + parsesSom.hashCode()
        result = 31 * result + visTilBruker.hashCode()
        return result
    }
}