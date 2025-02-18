package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

data class DagensTallDto(
    val hovedgruppe: DagensTallHovedgruppe,
    val undergruppe: DagensTallUndergruppe,
    val nyeIDag: Long,
    val ferdigstilteIDag: Long,
    val nyeSiste7Dager: Long,
    val ferdigstilteSiste7Dager: Long,
)
