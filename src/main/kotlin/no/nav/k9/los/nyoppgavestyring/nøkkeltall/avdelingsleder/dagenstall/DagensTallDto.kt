package no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.dagenstall

data class DagensTallDto(
    val hovedgruppe: DagensTallHovedgruppe,
    val undergruppe: DagensTallUndergruppe,

    val serier: Map<String, Pair<DagensTallKortDto, DagensTallKortDto>>,
    val månedSerier: Map<String, Pair<DagensTallKortDto, DagensTallKortDto>>
)

data class DagensTallKortDto(
    val hovedtall: DagensTallLinjeDto,
    val linjer: List<DagensTallLinjeDto>,
)

data class DagensTallLinjeDto(
    val visningsnavn: String,
    val verdi: Long,
)