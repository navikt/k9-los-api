package no.nav.k9.los.oppgavedefinisjon.oppgavetype

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class OppgavetyperDto (
    val område: Områder,
    val definisjonskilde: String,
    val oppgavetyper: Set<OppgavetypeDto>
)
