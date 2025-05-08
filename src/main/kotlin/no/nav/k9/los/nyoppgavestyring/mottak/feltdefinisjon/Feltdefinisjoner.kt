package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder.GyldigeTransientFeltutleder

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
                visningsnavn = feltdefinisjonDto.visningsnavn,
                listetype = feltdefinisjonDto.listetype,
                tolkesSom = feltdefinisjonDto.tolkesSom,
                visTilBruker = feltdefinisjonDto.visTilBruker,
                kokriterie = feltdefinisjonDto.kokriterie,
                kodeverkreferanse = feltdefinisjonDto.kodeverkreferanse?.let { kodeverkreferanseDto -> Kodeverkreferanse(kodeverkreferanseDto) },
                transientFeltutleder = feltdefinisjonDto.transientFeltutleder?.let { GyldigeTransientFeltutleder.hentFeltutleder(it) }
            )
        }.toSet()
    )

    fun hentFeltdefinisjon(eksternId: String) : Feltdefinisjon {
        return feltdefinisjoner.firstOrNull { feltdefinisjon -> feltdefinisjon.eksternId == eksternId }
            ?: throw IllegalArgumentException("Finner ikke omsøkt feltdefinisjon: $eksternId for område: ${område.eksternId}")
    }

    fun finnForskjeller(innkommendeFeltdefinisjoner: Feltdefinisjoner): Triple<Set<Feltdefinisjon>, Set<Feltdefinisjon>, Set<Feltdefinisjon>> {
        if (innkommendeFeltdefinisjoner.område != this.område) {
            throw IllegalStateException("Kan ikke sammenligne datatyper på tvers av områder. Dette skal være separate sett")
        }
        val leggtilListe = mutableSetOf<Feltdefinisjon>()
        val oppdaterListe = mutableSetOf<Feltdefinisjon>()
        val slettListe = mutableSetOf<Feltdefinisjon>()
        innkommendeFeltdefinisjoner.feltdefinisjoner.forEach { innkommende ->
            val eksisterende = feltdefinisjoner.find { it.eksternId == innkommende.eksternId }
            if (eksisterende == null) {
                leggtilListe.add(innkommende)
            } else { // finnes i liste, men kanskje forskjellig?
                val ulikeVerdier = eksisterende != innkommende
                if (ulikeVerdier) {
                    oppdaterListe.add(innkommende)
                }
            }
        }

        feltdefinisjoner.forEach{ eksisterende ->
            innkommendeFeltdefinisjoner.feltdefinisjoner.find { innkommendeFeltdefinisjon ->
                innkommendeFeltdefinisjon.eksternId == eksisterende.eksternId
            }?: slettListe.add(eksisterende)
        }

        return Triple(slettListe, oppdaterListe, leggtilListe)
    }

}