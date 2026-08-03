package no.nav.k9.los.oppgavedefinisjon.oppgavetype

import no.nav.k9.los.oppgavemottak.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.oppgavedefinisjon.omraade.Område

class Oppgavetype(
    val id: Long? = null,
    val eksternId: String,
    val område: Område,
    val definisjonskilde: String,
    val oppgavebehandlingsUrlTemplate: String?,
    val oppgavefelter: Set<Oppgavefelt>,
) {
    // Indekserte oppslag, slik at vi unngår O(felter) lineærsøk pr feltverdi-rad ved load.
    private val feltByEksternId: Map<String, Oppgavefelt> by lazy {
        oppgavefelter.associateBy { it.feltDefinisjon.eksternId }
    }
    private val feltById: Map<Long, Oppgavefelt> by lazy {
        oppgavefelter.mapNotNull { felt -> felt.id?.let { id -> id to felt } }.toMap()
    }

    fun hentFelt(feltdefinisjonId: String): Oppgavefelt {
        return feltByEksternId[feltdefinisjonId]
            ?: throw NoSuchElementException("Fant ikke oppgavefelt med feltdefinisjon eksternId=$feltdefinisjonId")
    }

    fun hentFeltById(oppgavefeltId: Long): Oppgavefelt {
        return feltById[oppgavefeltId]
            ?: throw NoSuchElementException("Fant ikke oppgavefelt med id=$oppgavefeltId")
    }

    constructor(
        dto: OppgavetypeDto,
        definisjonskilde: String,
        område: Område,
        oppgavebehandlingsUrlTemplate: String,
        feltdefinisjoner: Feltdefinisjoner,
        gyldigeFeltutledere: GyldigeFeltutledere
    ) : this(
        eksternId = dto.id,
        område = område,
        definisjonskilde = definisjonskilde,
        oppgavebehandlingsUrlTemplate = oppgavebehandlingsUrlTemplate,
        oppgavefelter = dto.oppgavefelter.map { innkommendeFeltdefinisjon ->
            Oppgavefelt(
                feltDefinisjon = feltdefinisjoner.feltdefinisjoner.find { eksisterendeFeltdefinisjon ->
                    eksisterendeFeltdefinisjon.eksternId == innkommendeFeltdefinisjon.id
                } ?: throw IllegalStateException("Omsøkt feltdefinisjon finnes ikke: ${innkommendeFeltdefinisjon.id}"),
                visPåOppgave = innkommendeFeltdefinisjon.visPåOppgave,
                påkrevd = innkommendeFeltdefinisjon.påkrevd,
                defaultverdi = innkommendeFeltdefinisjon.defaultverdi,
                feltutleder = innkommendeFeltdefinisjon.feltutleder?.let { gyldigeFeltutledere.hentFeltutleder(innkommendeFeltdefinisjon.feltutleder) }
            )
        }.toSet()
    )
}