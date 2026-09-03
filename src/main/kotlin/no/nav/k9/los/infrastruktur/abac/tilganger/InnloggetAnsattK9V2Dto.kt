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
    fun tilganger() = Tilganger(
        basis = kanVeilede || k9SaksbehandlerTilgang.kanSaksbehandle,
        kode6 = kanBehandleKode6,
        oppgavestyring = kanOppgavestyre,
        reservering = k9SaksbehandlerTilgang.kanSaksbehandle,
        drift = kanDrifte,
    )
}
