package no.nav.k9.los.db.util

object InClauseHjelper {

    fun <T> tilParameternavnMedCast(input: Collection<T>, prefix: String, castTilType: String): String {
        return tilParameternavnListe(input, "cast(:$prefix", " as $castTilType)").joinToString(",")
    }

    fun <T> tilParameternavn(input: Collection<T>, prefix: String): String {
        return tilParameternavnListe(input, ":$prefix").joinToString(",")
    }

    fun <T> parameternavnTilVerdierMap(input: Collection<T>, prefix: String): Map<String, T> {
        return tilParameternavnListe(input, prefix).zip(input).associateBy({ it.first }, { it.second })
    }

    private fun <T> tilParameternavnListe(input: Collection<T>, prefix: String, postfix: String = ""): List<String> {
        check(input.isNotEmpty())
        return IntRange(1, input.size).map { "$prefix$it$postfix" }

    }
}