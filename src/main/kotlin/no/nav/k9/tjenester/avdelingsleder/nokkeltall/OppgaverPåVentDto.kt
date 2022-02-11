package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import java.time.LocalDate

object OppgaverPåVentDto {
    data class PåVentResponse(
        val påVent: List<PerBehandlingDto>,
        val påVentMedVenteårsak: List<PerVenteårsakDto>
    )

    data class PerBehandlingDto(
        val fagsakYtelseType: FagsakYtelseType,
        val behandlingType: BehandlingType,
        val frist: LocalDate,
        val antall: Int
    )

    data class PerVenteårsakDto(
        val fagsakYtelseType: FagsakYtelseType,
        val behandlingType: BehandlingType,
        val frist: LocalDate,
        val venteårsak: String,
        val antall: Int
    )
}
