package no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon

import no.nav.k9.nyoppgavestyring.mottak.omraade.Område

class Feltdefinisjoner(
    val område: Område,
    val feltdefinisjoner: Set<Feltdefinisjon>
) {

    constructor(feltdefinisjonerDto: FeltdefinisjonerDto, område: Område) : this(
        område = område,
        feltdefinisjoner = feltdefinisjonerDto.feltdefinisjoner.map { feltdefinisjonDto ->
            Feltdefinisjon(
                eksternId = feltdefinisjonDto.id,
                område = område,
                listetype = feltdefinisjonDto.listetype,
                tolkesSom = feltdefinisjonDto.tolkesSom,
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
            innkommendeFeltdefinisjoner.feltdefinisjoner.find { innkommendeFeltdefinisjon ->
                innkommendeFeltdefinisjon.eksternId.equals(eksisterende.eksternId)
            }?: slettListe.add(eksisterende)
        }

        return Pair(slettListe, leggtilListe)
    }

}