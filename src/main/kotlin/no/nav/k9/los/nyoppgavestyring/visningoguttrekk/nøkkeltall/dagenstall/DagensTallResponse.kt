package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.KodeOgNavn
import java.time.LocalDateTime

sealed class DagensTallResponse {
    data class Suksess(
        val oppdatertTidspunkt: LocalDateTime,
        val hovedgrupper: List<KodeOgNavn>,
        val undergrupper: List<KodeOgNavn>,
        val tall: List<DagensTallDto>
    ) : DagensTallResponse()

    data class Feil(val feilmelding: String): DagensTallResponse()
}