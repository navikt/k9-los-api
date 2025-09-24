package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import java.time.LocalDateTime

data class StatusFordelingResponse(
    val oppdatertTidspunkt: LocalDateTime = LocalDateTime.now(),
    val tall: List<StatusFordelingDto> = emptyList(),
) {
    val grupper: List<KodeOgNavn> = StatusGruppe.entries.map { KodeOgNavn(it.name, it.tekst) }
}
