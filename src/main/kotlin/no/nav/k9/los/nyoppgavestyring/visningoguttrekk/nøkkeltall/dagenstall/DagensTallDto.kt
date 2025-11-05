package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

data class DagensTallDto(
    val hovedgruppe: DagensTallHovedgruppe,
    val undergruppe: DagensTallUndergruppe,

    // 1 dag
    val nyeIDag: Long,
    val ferdigstilteIDag: Long,
    val ferdigstilteHelautomatiskIDag: Long,

    // 1 uke
    val nyeSiste7Dager: Long,
    val ferdigstilteSiste7Dager: Long,
    val ferdigstilteHelautomatiskSiste7Dager: Long,

    // 2 uker
    val nyeSiste2Uker: Long,
    val ferdigstilteSiste2Uker: Long,
    val ferdigstilteHelautomatiskSiste2Uker: Long,

    // 4 uker
    val nyeSiste4Uker: Long,
    val ferdigstilteSiste4Uker: Long,
    val ferdigstilteHelautomatiskSiste4Uker: Long,
)
