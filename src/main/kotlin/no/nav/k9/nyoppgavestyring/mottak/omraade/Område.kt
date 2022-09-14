package no.nav.k9.nyoppgavestyring.mottak.omraade

class Område (
    val id: Long? = null,
    val eksternId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Område

        if (eksternId != other.eksternId) return false

        return true
    }

    override fun hashCode(): Int {
        return eksternId.hashCode()
    }
}
