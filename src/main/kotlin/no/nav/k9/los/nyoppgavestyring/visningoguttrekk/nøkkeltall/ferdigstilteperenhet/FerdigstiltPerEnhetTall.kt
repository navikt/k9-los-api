package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

import java.time.LocalDate

data class FerdigstiltPerEnhetTall(
    val dato: LocalDate,
    val enhet: String,
    val antall: Int,
    val gruppe: PerEnhetGruppe
)