package no.nav.k9.los.nyoppgavestyring.ko.dto

import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery

class OppgaveKo(
    val id: Long,
    val versjon: Long,
    val tittel: String,
    val beskrivelse: String,
    val oppgaveQuery: OppgaveQuery,
    val frittValgAvOppgave: Boolean,
    val saksbehandlere: List<String>
)