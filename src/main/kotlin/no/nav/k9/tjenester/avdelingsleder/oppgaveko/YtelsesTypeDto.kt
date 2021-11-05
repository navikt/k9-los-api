package no.nav.k9.tjenester.avdelingsleder.oppgaveko

data class YtelsesTypeDto(
    val id: String,
    val fagsakYtelseType: Array<String>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as YtelsesTypeDto

        if (id != other.id) return false
        if (fagsakYtelseType != null) {
            if (other.fagsakYtelseType == null) return false
            if (!fagsakYtelseType.contentEquals(other.fagsakYtelseType)) return false
        } else if (other.fagsakYtelseType != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (fagsakYtelseType?.contentHashCode() ?: 0)
        return result
    }
}
