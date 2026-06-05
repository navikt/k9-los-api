package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import java.time.LocalDateTime

data class ForlengReservasjonDto(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val kommentar: String?,
    val nyTilDato: LocalDateTime?,
)