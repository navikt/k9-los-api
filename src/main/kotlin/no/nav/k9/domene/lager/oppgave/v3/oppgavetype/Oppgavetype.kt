package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon.Feltdefinisjoner

class Oppgavetype(
    val id: String,
    val oppgavefelter: Set<Oppgavefelt>
) {

    constructor(dto: OppgavetypeDTO, feltdefinisjoner: Feltdefinisjoner) : this(
        id = dto.navn,
        oppgavefelter = dto.oppgavefelter.map { innkommendeFeltdefinisjon ->
            Oppgavefelt(
                feltDefinisjon = feltdefinisjoner.feltdefinisjoner.find { eksisterendeFeltdefinisjon ->
                    eksisterendeFeltdefinisjon.navn == innkommendeFeltdefinisjon.navn
                }.takeIf { feltdefinisjon -> feltdefinisjon != null } ?: throw IllegalStateException("Omsøkt feltdefinisjon finnes ikke"),
                visPåOppgave = innkommendeFeltdefinisjon.visPåOppgave,
                påkrevd = innkommendeFeltdefinisjon.påkrevd
            )
        }.toSet()
    )
}