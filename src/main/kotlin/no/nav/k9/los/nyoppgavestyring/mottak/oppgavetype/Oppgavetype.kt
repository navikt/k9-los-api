package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import no.nav.k9.los.nyoppgavestyring.feltutledere.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import kotlin.reflect.full.createInstance

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
                feltutleder = innkommendeFeltdefinisjon.feltutleder?.let { GyldigeFeltutledere.hentFeltutleder(innkommendeFeltdefinisjon.feltutleder) }
            )
        }.toSet()
    )

    companion object {
        private fun utledFeltutleder(feltutleder: String): String {
            val kClass = GyldigeFeltutledere.feltutledere[feltutleder]
            return if (kClass != null) feltutleder else throw IllegalArgumentException("Utleder finnes ikke: $feltutleder")
        }
    }

    fun finnOppgavefelt(eksternId: String) : Oppgavefelt? {
        return oppgavefelter.find { it.feltDefinisjon.eksternId.equals(eksternId) }
    }
}