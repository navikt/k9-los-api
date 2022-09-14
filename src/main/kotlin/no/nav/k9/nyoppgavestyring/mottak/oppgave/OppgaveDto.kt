package no.nav.k9.nyoppgavestyring.mottak.oppgave

import java.time.LocalDateTime

data class OppgaveDto(
    val id: String,
    val versjon: String,
    val område: String,
    val kildeområde: String,
    val type: String,
    val status: String,
    val endretTidspunkt: LocalDateTime,
    val feltverdier: List<OppgaveFeltverdiDto>
)
