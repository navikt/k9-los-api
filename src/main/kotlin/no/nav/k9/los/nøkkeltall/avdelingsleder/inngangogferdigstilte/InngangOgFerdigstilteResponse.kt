package no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte

import no.nav.k9.los.nøkkeltall.KodeOgNavn
import java.time.LocalDateTime

data class InngangOgFerdigstilteResponse(
    val oppdatertTidspunkt: LocalDateTime?,
    val hovedgrupper: List<KodeOgNavn>,
    val undergrupper: List<KodeOgNavn>,
    val tall: List<InngangOgFerdigstilteDto>,
)

data class InngangOgFerdigstilteDto(
    val hovedgruppe: String,
    val undergruppe: String,
    val serier: Map<String, Pair<InngangOgFerdigstilteKortDto, InngangOgFerdigstilteKortDto>>,
    val månedSerier: Map<String, Pair<InngangOgFerdigstilteKortDto, InngangOgFerdigstilteKortDto>> = emptyMap(),
)

data class InngangOgFerdigstilteKortDto(
    val hovedtall: InngangOgFerdigstilteLinjeDto,
    val linjer: List<InngangOgFerdigstilteLinjeDto> = emptyList(),
)

data class InngangOgFerdigstilteLinjeDto(
    val visningsnavn: String,
    val verdi: Long,
)
