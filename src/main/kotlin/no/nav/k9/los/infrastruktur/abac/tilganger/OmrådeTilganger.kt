package no.nav.k9.los.infrastruktur.abac.tilganger

/**
 * Los sin interne, områdesnøytrale modell for tilganger til innlogget ansatt.
 * Mappes fra PDP-ens områdespesifikke DTO-er (K9/ung).
 */
data class OmrådeTilganger(
    val harBasisTilgang: Boolean,
    val harTilgangTilKode6: Boolean,
    val erOppgavestyrer: Boolean,
    val harTilgangTilReserveringAvOppgaver: Boolean,
    val kanLeggeUtDriftsmelding: Boolean,
) {
    companion object {
        fun forK9(dto: InnloggetAnsattK9V2Dto) = OmrådeTilganger(
            harBasisTilgang = dto.kanVeilede || dto.k9SaksbehandlerTilgang.kanSaksbehandle,
            harTilgangTilKode6 = dto.kanBehandleKode6,
            erOppgavestyrer = dto.kanOppgavestyre,
            harTilgangTilReserveringAvOppgaver = dto.k9SaksbehandlerTilgang.kanSaksbehandle,
            kanLeggeUtDriftsmelding = dto.kanDrifte,
        )

        fun forAktivitetspenger(dto: InnloggetAnsattUngV2Dto) = OmrådeTilganger(
            harBasisTilgang = dto.kanVeiledeAktivitetspenger
                    || dto.aktivitetspengerDel1SaksbehandlerTilgang.kanSaksbehandle
                    || dto.aktivitetspengerDel2SaksbehandlerTilgang.kanSaksbehandle,
            harTilgangTilKode6 = dto.kanBehandleKode6,
            erOppgavestyrer = dto.kanOppgavestyreAktivitetspenger,
            harTilgangTilReserveringAvOppgaver = dto.aktivitetspengerDel1SaksbehandlerTilgang.kanSaksbehandle
                    || dto.aktivitetspengerDel2SaksbehandlerTilgang.kanSaksbehandle,
            kanLeggeUtDriftsmelding = dto.kanDrifte,
        )
    }
}
