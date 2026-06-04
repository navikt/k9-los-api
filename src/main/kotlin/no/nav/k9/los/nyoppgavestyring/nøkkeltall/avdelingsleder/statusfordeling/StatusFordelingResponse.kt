package no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.statusfordeling

import java.time.LocalDateTime

data class StatusFordelingResponse(
    val oppdatertTidspunkt: LocalDateTime = LocalDateTime.now(),
    val tall: List<StatuskortDto> = emptyList(),
)
