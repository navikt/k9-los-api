package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import java.time.LocalDateTime

sealed class FerdigstiltPerEnhetResponse {
    data class Suksess(
        val oppdatertTidspunkt: LocalDateTime,
        val grupper: List<KodeOgNavn>,
        val tall: List<FerdigstiltPerEnhetTall>
    ) : FerdigstiltPerEnhetResponse()

    data class Feil(val feilmelding: String) : FerdigstiltPerEnhetResponse()
}