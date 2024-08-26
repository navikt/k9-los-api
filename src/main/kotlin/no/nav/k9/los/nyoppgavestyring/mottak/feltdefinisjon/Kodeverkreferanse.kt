package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

class Kodeverkreferanse(
    val område: String,
    val eksternId: String
) {
    constructor(kodeverkReferanseDto: KodeverkReferanseDto): this(
        område = kodeverkReferanseDto.område,
        eksternId = kodeverkReferanseDto.eksternId
    )

    constructor(kodeverk: Kodeverk): this (
        område = kodeverk.område.eksternId,
        eksternId = kodeverk.eksternId
    )

    constructor(databasestreng: String): this (
        område = databasestreng.substringBefore("."),
        eksternId = databasestreng.substringAfter(".")
    )

    fun toDatabasestreng(): String {
        return "$område.$eksternId"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Kodeverkreferanse

        if (område != other.område) return false
        return eksternId == other.eksternId
    }

    override fun hashCode(): Int {
        var result = område.hashCode()
        result = 31 * result + eksternId.hashCode()
        return result
    }
}