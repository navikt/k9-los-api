package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import no.nav.k9.los.nyoppgavestyring.feltutledere.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto

class Oppgavetype(
    val id: Long? = null,
    val eksternId: String,
    val område: Område,
    val definisjonskilde: String,
    val oppgavefelter: Set<Oppgavefelt>
) {

    constructor(dto: OppgavetypeDto, definisjonskilde: String, område: Område, feltdefinisjoner: Feltdefinisjoner) : this(
        eksternId = dto.id,
        område = område,
        definisjonskilde = definisjonskilde,
        oppgavefelter = dto.oppgavefelter.map { innkommendeFeltdefinisjon ->
            Oppgavefelt(
                feltDefinisjon = feltdefinisjoner.feltdefinisjoner.find { eksisterendeFeltdefinisjon ->
                    eksisterendeFeltdefinisjon.eksternId == innkommendeFeltdefinisjon.id
                } ?: throw IllegalStateException("Omsøkt feltdefinisjon finnes ikke"),
                visPåOppgave = innkommendeFeltdefinisjon.visPåOppgave,
                påkrevd = innkommendeFeltdefinisjon.påkrevd,
                feltutleder = innkommendeFeltdefinisjon.feltutleder?.let { utledFeltutleder(it) }
            )
        }.toSet()
    )

    companion object {
        private fun utledFeltutleder(feltutleder: String): String {
            val kClass = GyldigeFeltutledere.feltutledere[feltutleder]
            return if (kClass != null) feltutleder else throw IllegalArgumentException("Utleder finnes ikke: $feltutleder")
        }
    }

    fun valider(oppgaveDto: OppgaveDto) {

        oppgaveDto.feltverdier.forEach { dtofelt ->
            oppgavefelter.find { it.feltDefinisjon.eksternId.equals(dtofelt.nøkkel) }
                ?: throw IllegalArgumentException("Kan ikke oppgi feltverdi som ikke er spesifisert i oppgavetypen: ${dtofelt.nøkkel}")
            //TODO: valider at feltverdi kan tolkes som angitt feltdefinisjon sin tolkesSom
        }

        oppgavefelter
            .filter { it.påkrevd }
            .forEach { obligatoriskFelt ->
                oppgaveDto.feltverdier
                    .find { it.nøkkel.equals(obligatoriskFelt.feltDefinisjon.eksternId) }
                    ?: throw IllegalArgumentException("Oppgaven mangler obligatorisk felt " + obligatoriskFelt.feltDefinisjon.eksternId)
            }
    }
}