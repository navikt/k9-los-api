package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.uthenting.OppgaveNøkkelDto
import java.time.LocalDateTime

data class ForlengReservasjonDto(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val kommentar: String?,
    val nyTilDato: LocalDateTime?,
)