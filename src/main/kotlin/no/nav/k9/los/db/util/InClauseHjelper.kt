package no.nav.k9.los.db.util

object InClauseHjelper {

    val STØRRELSER = listOf(2, 4, 8, 16, 32, 64).sorted()

    fun <T> tilParameternavnMedCast(input: Collection<T>, prefix: String, castTilType: String): String {
        return tilParameternavnListe(input, "cast(:" + prefix, " as $castTilType)").joinToString(",")
    }

    fun <T> tilParameternavn(input: Collection<T>, prefix: String): String {
        return tilParameternavnListe(input, ":" + prefix).joinToString(",")
    }

    fun <T> parameternavnTilVerdierMap(input: Collection<T>, prefix: String): Map<String, T> {
        return tilParameternavnListe(input, prefix).zip(rundOppStørrelse(input)).associateBy({ it.first }, { it.second })
    }

    private fun <T> tilParameternavnListe(input: Collection<T>, prefix: String, postfix: String = ""): List<String> {
        check(input.isNotEmpty())
        return IntRange(1, rundOppStørrelse(input.size)).map { "$prefix$it$postfix" }
    }

    private fun <T> rundOppStørrelse(input : Collection<T>) : List<T> {
        if (input.isEmpty()){
            return emptyList()
        }
        val resultat = ArrayList(input)
        val sisteElement = input.last()
        val rundetStørrelse = rundOppStørrelse(input.size)
        while (resultat.size < rundetStørrelse){
            resultat.add(sisteElement)
        }
        return resultat;
    }

    private fun rundOppStørrelse(antall: Int):Int {
        for (s in STØRRELSER) {
            if (antall <= s){
                return s;
            }
        }
        val største = STØRRELSER.last()
        //multiplum av største
        return største * ((antall + største -1) / største)
    }
}