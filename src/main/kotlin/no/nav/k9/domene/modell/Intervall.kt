package no.nav.k9.domene.modell

data class Intervall<T : Number>(val fom: T? = null, val tom: T? = null) {
    init {
        if (fom == null && tom == null) throw IllegalArgumentException("både fom og tom kan ikke være null")
    }

    fun erUtenfor(n: T) : Boolean {
        if (fom == null) return n.toDouble() > tom!!.toDouble()
        if (tom == null) return n.toDouble() < fom.toDouble()
        return n.toDouble() < fom.toDouble() || n.toDouble() > tom.toDouble()
    }

}