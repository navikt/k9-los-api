package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon.Feltdefinisjoner
import no.nav.k9.domene.lager.oppgave.v3.oppgave.OppgaveDto

class Oppgavetype(
    val id: String,
    val definisjonskilde: String,
    val oppgavefelter: Set<Oppgavefelt>
) {

    constructor(dto: OppgavetypeDto, definisjonskilde: String, feltdefinisjoner: Feltdefinisjoner) : this(
        id = dto.navn,
        definisjonskilde = definisjonskilde,
        oppgavefelter = dto.oppgavefelter.map { innkommendeFeltdefinisjon ->
            Oppgavefelt(
                feltDefinisjon = feltdefinisjoner.feltdefinisjoner.find { eksisterendeFeltdefinisjon ->
                    eksisterendeFeltdefinisjon.navn == innkommendeFeltdefinisjon.navn
                }.takeIf { feltdefinisjon -> feltdefinisjon != null }
                    ?: throw IllegalStateException("Omsøkt feltdefinisjon finnes ikke"),
                visPåOppgave = innkommendeFeltdefinisjon.visPåOppgave,
                påkrevd = innkommendeFeltdefinisjon.påkrevd
            )
        }.toSet()
    )

    fun valider(oppgaveDto: OppgaveDto) {
        oppgaveDto.feltverdier.forEach { dtofelt ->
            oppgavefelter.find { it.feltDefinisjon.navn.equals(dtofelt.nøkkel) }
                ?: throw IllegalArgumentException("Kan ikke oppgi feltverdi som ikke er spesifisert i oppgavetypen")
        }

        oppgavefelter
            .filter { it.påkrevd }
            .forEach { obligatoriskFelt ->
                oppgaveDto
                    .feltverdier
                    .find { it.nøkkel.equals(obligatoriskFelt.feltDefinisjon.navn) }
                    ?: throw IllegalArgumentException("Oppgaven mangler obligatorisk felt " + obligatoriskFelt.feltDefinisjon.navn)
            }
    }
}