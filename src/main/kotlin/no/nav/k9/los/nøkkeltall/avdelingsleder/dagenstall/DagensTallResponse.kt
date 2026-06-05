package no.nav.k9.los.nøkkeltall.avdelingsleder.dagenstall

import no.nav.k9.los.nøkkeltall.KodeOgNavn
import java.time.LocalDateTime

data class DagensTallResponse(
    val oppdatertTidspunkt: LocalDateTime?,
    val hovedgrupper: List<KodeOgNavn>,
    val undergrupper: List<KodeOgNavn>,
    val tall: List<DagensTallDto>
)