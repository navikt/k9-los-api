package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område

class Oppgavetype(
    val id: Long? = null,
    val eksternId: String,
    val område: Område,
    val definisjonskilde: String,
    val oppgavebehandlingsUrlTemplate: String?,
    val oppgavefelter: Set<Oppgavefelt>,
) {
    fun hentFelt(feltdefinisjonId: String): Oppgavefelt {
        return oppgavefelter.first { it.feltDefinisjon.eksternId == feltdefinisjonId }
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