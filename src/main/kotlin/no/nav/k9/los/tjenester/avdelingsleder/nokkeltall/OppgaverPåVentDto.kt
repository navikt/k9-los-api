package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.los.domene.lager.oppgave.Kodeverdi
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import java.time.LocalDate

object OppgaverPåVentDto {
    data class PåVentResponse(
        val påVent: List<PerBehandlingDto>,
        val påVentMedVenteårsak: List<PerVenteårsakDto>
    )

    data class PerBehandlingDto(
        val fagsakYtelseType: FagsakYtelseType,
        val behandlingType: BehandlingType,
        val frist: LocalDate?,
        val antall: Int
    )

    data class PerVenteårsakDto(
        val fagsakYtelseType: FagsakYtelseType,
        val behandlingType: BehandlingType,
        val frist: LocalDate?,
        val venteårsak: Venteårsak,
        val antall: Int
    )
}

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Venteårsak(override val kode: String, override val navn: String) : Kodeverdi {
    AVV_DOK("AVV_DOK", "Avventer dokumentasjon"),
    VENT_MANGL_FUNKSJ_SAKSBEHANDLER("VENT_MANGL_FUNKSJ_SAKSBEHANDLER", "Manglende funksjonalitet i løsningen"),
    VENTER_SVAR_INTERNT("VENTER_SVAR_INTERNT", "Meldt i Porten eller Teams"),
    AUTOMATISK_SATT_PA_VENT("AUTOMATISK", "Automatisk satt på vent"),
    UKJENT("UKJENT", "Mangler venteårsak");

    override val kodeverk = "VENTEÅRSAK_TYPE"
}