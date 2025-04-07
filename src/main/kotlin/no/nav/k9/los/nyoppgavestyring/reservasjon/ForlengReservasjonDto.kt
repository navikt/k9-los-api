package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import java.time.LocalDateTime

data class ForlengReservasjonDto(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val kommentar: String?,
    val nyTilDato: LocalDateTime?,
)