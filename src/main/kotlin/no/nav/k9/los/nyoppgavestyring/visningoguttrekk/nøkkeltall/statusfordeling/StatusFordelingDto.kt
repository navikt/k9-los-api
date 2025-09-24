package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling

data class StatusFordelingDto(
    val gruppe: StatusGruppe,
    val antallTotalt: Long,
    val antallÅpne: Long,
    val antallVenter: Long,
    val antallVenterKabal: Long,
    val antallVenterAnnet: Long,
    val antallUavklart: Long
)
