package no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet

import no.nav.k9.los.nøkkeltall.KodeOgNavn
import java.time.LocalDateTime

data class FerdigstiltePerEnhetResponse(
    val oppdatertTidspunkt: LocalDateTime? = null,
    val grupper: List<KodeOgNavn> = FerdigstiltePerEnhetGruppe.entries.map { KodeOgNavn(it.name, it.navn) },
    val kolonner: List<String> = emptyList(),
    val serier: List<FerdigstiltePerEnhetSerie> = emptyList(),
)