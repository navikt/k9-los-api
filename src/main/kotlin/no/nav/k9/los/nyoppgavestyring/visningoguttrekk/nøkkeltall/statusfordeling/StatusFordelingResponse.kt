package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling

import java.time.LocalDateTime

data class StatusFordelingResponse(
    val oppdatertTidspunkt: LocalDateTime = LocalDateTime.now(),
    val tall: List<StatuskortDto> = emptyList(),
)
