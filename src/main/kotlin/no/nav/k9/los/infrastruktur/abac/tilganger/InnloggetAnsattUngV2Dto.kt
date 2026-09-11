package no.nav.k9.los.infrastruktur.abac.tilganger

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

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
