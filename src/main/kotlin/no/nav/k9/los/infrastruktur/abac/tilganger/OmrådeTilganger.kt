package no.nav.k9.los.infrastruktur.abac.tilganger

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

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
        /** Rød sone: mappingen her er Los sin autoritative tolkning av PDP-tilganger. */
        fun fraK9(dto: InnloggetAnsattK9V2Dto) = OmrådeTilganger(
            harBasisTilgang = dto.kanVeilede || dto.k9SaksbehandlerTilgang.kanSaksbehandle,
            harTilgangTilKode6 = dto.kanBehandleKode6,
            erOppgavestyrer = dto.kanOppgavestyre,
            // Veiledere skal ikke kunne reservere, verken i K9 eller aktivitetspenger.
            harTilgangTilReserveringAvOppgaver = dto.k9SaksbehandlerTilgang.kanSaksbehandle,
            kanLeggeUtDriftsmelding = dto.kanDrifte,
        )

        fun fraUng(dto: InnloggetAnsattUngV2Dto) = OmrådeTilganger(
            // Kun aktivitetspenger-tilganger teller for basistilgang i Los-området
            // AKTIVITETSPENGER; ungdomsprogramytelse er et separat domene.
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

internal data class TilgangCacheKey(val navIdent: String, val område: Områder)
