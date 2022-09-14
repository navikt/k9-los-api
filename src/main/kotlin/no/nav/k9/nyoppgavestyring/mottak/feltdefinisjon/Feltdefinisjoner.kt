package no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon

import no.nav.k9.nyoppgavestyring.mottak.omraade.Område


class Feltdefinisjoner(
    val område: Område,
    val feltdefinisjoner: Set<no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon>
) {

    constructor(feltdefinisjonerDto: FeltdefinisjonerDto, område: Område): this(
        område = område,
        feltdefinisjoner = feltdefinisjonerDto.feltdefinisjoner.map { feltdefinisjonDto ->
            no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon(
                eksternId = feltdefinisjonDto.id,
                område = område,
                listetype = feltdefinisjonDto.listetype,
                tolkesSom = feltdefinisjonDto.tolkesSom,
                visTilBruker = feltdefinisjonDto.visTilBruker
            )
        }.toSet()
    )

    fun finnForskjeller(innkommendeFeltdefinisjoner: Feltdefinisjoner): Pair<Set<no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon>, Set<no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon>> {
        if (!innkommendeFeltdefinisjoner.område.equals(this.område)) {
            throw IllegalStateException("Kan ikke sammenligne datatyper på tvers av områder. Dette skal være separate sett")
        }
        val leggtilListe = mutableSetOf<no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon>()
        val slettListe = mutableSetOf<no.nav.k9.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon>()
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