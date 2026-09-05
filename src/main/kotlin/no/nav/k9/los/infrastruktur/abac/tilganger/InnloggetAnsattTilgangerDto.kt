package no.nav.k9.los.infrastruktur.abac.tilganger

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

/**
 * Speiler InnloggetAnsattK9V2Dto fra sif-abac-pdp (kontrakt).
 * kanDrifte er på tidspunktet for skriving ikke med i PDP-kontrakten (bug), men er
 * garantert å komme. Default false gjør oss kompatible både før og etter PDP leverer feltet.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InnloggetAnsattK9V2Dto(
    val brukernavn: String,
    val navn: String,
    val kanVeilede: Boolean,
    val kanBehandleKode6: Boolean,
    val kanBehandleKode7: Boolean,
    val kanBehandleKodeEgenAnsatt: Boolean,
    val kanLeseHistoriskSak: Boolean,
    val funksjonellTid: LocalDateTime,
    val skalViseDetaljerteFeilmeldinger: Boolean,
    val k9SaksbehandlerTilgang: SaksbehandlerTilgangDto,
    val kanOppgavestyre: Boolean,
    val kanDrifte: Boolean = false,
)

/** Speiler InnloggetAnsattUngV2Dto fra sif-abac-pdp (kontrakt). */
@JsonIgnoreProperties(ignoreUnknown = true)
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class SaksbehandlerTilgangDto(
    val kanSaksbehandle: Boolean,
    val kanBeslutte: Boolean,
    val kanOverstyre: Boolean,
)
