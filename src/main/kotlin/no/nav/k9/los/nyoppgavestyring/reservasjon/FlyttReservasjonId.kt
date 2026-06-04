package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.uthenting.OppgaveNøkkelDto

data class FlyttReservasjonId(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val brukerIdent: String,
    val begrunnelse: String)
