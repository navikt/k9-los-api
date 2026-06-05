package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import java.time.LocalDate

data class ReservasjonEndringDto (
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val brukerIdent: String? = null,
    val reserverTil: LocalDate? = null,
    val begrunnelse: String? = null
)
