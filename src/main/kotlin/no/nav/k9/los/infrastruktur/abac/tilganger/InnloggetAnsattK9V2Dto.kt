package no.nav.k9.los.infrastruktur.abac.tilganger

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class InnloggetAnsattK9V2Dto(
    val kanVeilede: Boolean,
    val kanBehandleKode6: Boolean,
    val k9SaksbehandlerTilgang: SaksbehandlerTilgangDto,
    val kanOppgavestyre: Boolean,
    val kanDrifte: Boolean,
) {
    fun tilTilganger() = Tilganger(
        basis = kanVeilede || k9SaksbehandlerTilgang.kanSaksbehandle,
        kode6 = kanBehandleKode6,
        oppgavestyring = kanOppgavestyre,
        reservering = k9SaksbehandlerTilgang.kanSaksbehandle,
        drift = kanDrifte,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class InnloggetAnsattUngV2Dto(
    val kanVeiledeAktivitetspenger: Boolean,
    val kanDrifte: Boolean,
    val kanBehandleKode6: Boolean,
    val aktivitetspengerDel1SaksbehandlerTilgang: SaksbehandlerTilgangDto,
    val aktivitetspengerDel2SaksbehandlerTilgang: SaksbehandlerTilgangDto,
    val kanOppgavestyreAktivitetspenger: Boolean,
) {
    fun tilTilganger(): Tilganger {
        val kanSaksbehandle = aktivitetspengerDel1SaksbehandlerTilgang.kanSaksbehandle ||
            aktivitetspengerDel2SaksbehandlerTilgang.kanSaksbehandle
        return Tilganger(
            basis = kanVeiledeAktivitetspenger || kanSaksbehandle,
            kode6 = kanBehandleKode6,
            oppgavestyring = kanOppgavestyreAktivitetspenger,
            reservering = kanSaksbehandle,
            drift = kanDrifte,
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class SaksbehandlerTilgangDto(val kanSaksbehandle: Boolean)
