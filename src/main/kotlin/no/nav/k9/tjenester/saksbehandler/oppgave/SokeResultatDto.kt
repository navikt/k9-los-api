package no.nav.k9.tjenester.saksbehandler.oppgave

import no.nav.k9.tjenester.fagsak.PersonDto

data class SokeResultatDto(
    val ikkeTilgang: Boolean,
    val person: PersonDto?,
    val oppgaver: MutableList<OppgaveDto>
)
