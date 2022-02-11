package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import com.fasterxml.jackson.annotation.JsonValue
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
        val venteårsak: Venteårsak,
        val antall: Int
    )
}

enum class Venteårsak(@JsonValue val kode: String, val navn: String) {
    AVV_DOK("AVV_DOK", "Avventer dokumentasjon"),
    VENT_MANGL_FUNKSJ_SAKSBEHANDLER("VENT_MANGL_FUNKSJ_SAKSBEHANDLER", "Settes på vent av saksbehandler pga. manglende funksjonalitet i løsningen"),
    ANNET_MANUELT_SATT_PA_VENT("ANNET_MANUELT", "Annen manuell venteårsak "),
    AUTOMATISK_SATT_PA_VENT("AUTOMATISK", "Automatisk satt på vent"),
    UKJENT("UKJENT", "Mangler venteårsak")
}