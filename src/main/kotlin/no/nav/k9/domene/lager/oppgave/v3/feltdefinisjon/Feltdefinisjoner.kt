package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

import no.nav.k9.domene.lager.oppgave.v3.omraade.Område

class Feltdefinisjoner(
    val område: Område,
    val feltdefinisjoner: Set<Feltdefinisjon>
) {

    constructor(feltdefinisjonerDto: FeltdefinisjonerDto, område: Område): this(
        område = område,
        feltdefinisjoner = feltdefinisjonerDto.feltdefinisjoner.map { feltdefinisjonDto ->
            Feltdefinisjon(
                eksternId = feltdefinisjonDto.id,
                område = område,
                listetype = feltdefinisjonDto.listetype,
                tolkesSom = feltdefinisjonDto.parsesSom,
                visTilBruker = feltdefinisjonDto.visTilBruker
            )
        }.toSet()
    )

    fun finnForskjeller(innkommendeFeltdefinisjoner: Feltdefinisjoner): Pair<Set<Feltdefinisjon>, Set<Feltdefinisjon>> {
        if (!innkommendeFeltdefinisjoner.område.equals(this.område)) {
            throw IllegalStateException("Kan ikke sammenligne datatyper på tvers av områder. Dette skal være separate sett")
        }
        val leggtilListe = mutableSetOf<Feltdefinisjon>()
        val slettListe = mutableSetOf<Feltdefinisjon>()
        innkommendeFeltdefinisjoner.feltdefinisjoner.forEach { innkommende ->
            val eksisterende = feltdefinisjoner.find { it.eksternId.equals(innkommende.eksternId) }
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

        feltdefinisjoner.forEach{ eksisterende ->
            val innkommende = innkommendeFeltdefinisjoner.feltdefinisjoner.find { it.eksternId.equals(eksisterende.eksternId) }
            if (innkommende == null) {
                slettListe.add(eksisterende)
            }
        }
        return Pair(slettListe, leggtilListe)
    }

}