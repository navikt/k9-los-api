package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto

data class FlyttReservasjonId(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val brukerIdent: String,
    val begrunnelse: String)
