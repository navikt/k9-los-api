package no.nav.k9.domene.lager.oppgave.v3.datatype

class Datatyper(
    val område: String,
    val datatyper: Set<Datatype>
) {
    fun finnForskjeller(innkommendeDatatyper: Datatyper): Pair<Set<Datatype>, Set<Datatype>> {
        if (!innkommendeDatatyper.område.equals(this.område)) {
            throw IllegalArgumentException("Kan ikke sammenligne datatyper på tvers av områder. Dette skal være separate sett")
        }
        val leggtilListe = mutableSetOf<Datatype>()
        val slettListe = mutableSetOf<Datatype>()
        innkommendeDatatyper.datatyper.forEach { innkommende ->
            val eksisterende = datatyper.find { it.id.equals(innkommende.id) }
            if (eksisterende == null) {
                leggtilListe.add(innkommende)
            } else { // finnes i liste, men kanskje forskjellig?
                val ulikeVerdier = !eksisterende.equals(innkommende)
                if (ulikeVerdier) {
                    slettListe.add(eksisterende)
                    leggtilListe.add(innkommende)
                }
            }
        }

        datatyper.forEach{ eksisterende ->
            val innkommende = innkommendeDatatyper.datatyper.find { it.id.equals(eksisterende.id) }
            if (innkommende == null) {
                slettListe.add(eksisterende)
            }
        }
        return Pair(slettListe, leggtilListe)
    }

}