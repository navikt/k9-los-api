package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery

data class DagensTallDto(
    val hovedgruppe: DagensTallHovedgruppe,
    val undergruppe: DagensTallUndergruppe,

    val serier: Map<String, Pair<DagensTallKortDto, DagensTallKortDto>>
)

data class DagensTallKortDto(
    val hovedtall: DagensTallLinjeDto,
    val linjer: List<DagensTallLinjeDto>,
)

data class DagensTallLinjeDto(
    val visningsnavn: String,
    val verdi: Long,
)