package no.nav.k9.los.nyoppgavestyring.nyeogferdigstilte

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import java.time.LocalDateTime

data class NyeOgFerdigstilteResponse(
    val oppdatertTidspunkt: LocalDateTime?,
    val grupper: List<KodeOgNavn> = NyeOgFerdigstilteGruppe.entries.map { KodeOgNavn(it.name, it.navn) },
    val kolonner: List<String> = emptyList(),
    val serier: List<NyeOgFerdigstilteSerie> = emptyList(),
)