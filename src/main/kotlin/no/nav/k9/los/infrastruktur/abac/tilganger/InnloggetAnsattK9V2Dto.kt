package no.nav.k9.los.infrastruktur.abac.tilganger

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class InnloggetAnsattK9V2Dto(
    val brukernavn: String = "",
    val navn: String = "",
    val kanVeilede: Boolean,
    val kanBehandleKode6: Boolean,
    val kanBehandleKode7: Boolean = false,
    val kanBehandleKodeEgenAnsatt: Boolean = false,
    val kanLeseHistoriskSak: Boolean = false,
    val funksjonellTid: LocalDateTime? = null,
    val skalViseDetaljerteFeilmeldinger: Boolean = false,
    val k9SaksbehandlerTilgang: SaksbehandlerTilgangDto,
    val kanOppgavestyre: Boolean,
    val kanDrifte: Boolean? = null,
) {
    fun tilTilganger() = Tilganger(
        basis = kanVeilede || k9SaksbehandlerTilgang.kanSaksbehandle,
        kode6 = kanBehandleKode6,
        oppgavestyring = kanOppgavestyre,
        reservering = k9SaksbehandlerTilgang.kanSaksbehandle,
        drift = kanDrifte ?: false,
    )
}

data class InnloggetAnsattUngV2Dto(
    val brukernavn: String,
    val navn: String,
    val kanVeiledeUngdomsprogramytelse: Boolean,
    val kanVeiledeAktivitetspenger: Boolean,
    val kanDrifte: Boolean,
    val erUngdomsprogramveileder: Boolean,
    val kanBehandleKode6: Boolean,
    val kanBehandleKode7: Boolean,
    val kanBehandleKodeEgenAnsatt: Boolean,
    val funksjonellTid: LocalDateTime,
    val skalViseDetaljerteFeilmeldinger: Boolean,
    val ungdomsprogramytelseSaksbehandlerTilgang: SaksbehandlerTilgangDto,
    val aktivitetspengerDel1SaksbehandlerTilgang: SaksbehandlerTilgangDto,
    val aktivitetspengerDel2SaksbehandlerTilgang: SaksbehandlerTilgangDto,
    val aktuelleYtelser: Set<String>,
    val kanOppgavestyreAktivitetspenger: Boolean,
)

data class SaksbehandlerTilgangDto(
    val kanSaksbehandle: Boolean,
    val kanBeslutte: Boolean,
    val kanOverstyre: Boolean,
)
