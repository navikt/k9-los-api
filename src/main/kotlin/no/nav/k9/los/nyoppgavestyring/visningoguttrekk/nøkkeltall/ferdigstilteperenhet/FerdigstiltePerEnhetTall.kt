package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

import java.time.LocalDate

data class FerdigstiltePerEnhetTall(
    val dato: LocalDate,
    val parameter: FerdigstiltParameter,
    val gruppe: FerdigstiltePerEnhetGruppe,
    val antall: Int,
)