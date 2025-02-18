package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import java.time.LocalDateTime

data class DagensTallResponse(
    val oppdatertTidspunkt: LocalDateTime?,
    val hovedgrupper: List<KodeOgNavn>,
    val undergrupper: List<KodeOgNavn>,
    val tall: List<DagensTallDto>
)