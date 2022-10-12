package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.tjenester.fagsak.PersonDto

data class SokeResultatDto(
    val ikkeTilgang: Boolean,
    val person: PersonDto?,
    val oppgaver: MutableList<OppgaveDto>
)
