package no.nav.k9.domene.lager.oppgave.v3.datatype

class Datatype(
    val id: String,
    val listetype: Boolean,
    val implementasjonstype: String,
    val visTilBruker: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Datatype

        if (id != other.id) return false
        if (listetype != other.listetype) return false
        if (implementasjonstype != other.implementasjonstype) return false
        if (visTilBruker != other.visTilBruker) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + listetype.hashCode()
        result = 31 * result + implementasjonstype.hashCode()
        result = 31 * result + visTilBruker.hashCode()
        return result
    }
}