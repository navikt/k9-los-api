package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.tjenester.saksbehandler.oppgave.Beskjed
import java.time.LocalDateTime

data class ReservasjonStatusDto(
    val reservasjonsnøkkel: String?,
    val erReservert: Boolean,
    val reservertFraTidspunkt: LocalDateTime?,
    val reservertTilTidspunkt: LocalDateTime?,
    val erReservertAvInnloggetBruker: Boolean,
    val reservertAvEpost: String?,
    val reservertAvIdent: String?,
    val reservertAvNavn: String?,
    val kanOverstyres: Boolean? = false,//TODO : Hva brukes denne til?
    val beskjed: Beskjed? = null//TODO : Hva brukes denne til?
) {
    constructor(reservasjonsnøkkel: String?, gyldigFra: LocalDateTime, gyldigTil: LocalDateTime, saksbehandlerSomHarReservasjon: Saksbehandler, innloggetBruker: Saksbehandler) : this (
        reservasjonsnøkkel = reservasjonsnøkkel,
        erReservert = true,
        reservertFraTidspunkt = gyldigFra,
        reservertTilTidspunkt = gyldigTil,
        erReservertAvInnloggetBruker = innloggetBruker.epost == saksbehandlerSomHarReservasjon.epost,
        reservertAvEpost = saksbehandlerSomHarReservasjon.epost,
        reservertAvIdent = saksbehandlerSomHarReservasjon.brukerIdent,
        reservertAvNavn = saksbehandlerSomHarReservasjon.navn,
        kanOverstyres = null,
        beskjed = null
    )

    companion object {
        fun blankIkkeTilgang() : ReservasjonStatusDto {
            return ReservasjonStatusDto(
                reservasjonsnøkkel = null,
                erReservert = false,
                reservertFraTidspunkt = null,
                reservertTilTidspunkt = null,
                erReservertAvInnloggetBruker = false,
                reservertAvEpost = null,
                reservertAvIdent = null,
                reservertAvNavn = null,
                kanOverstyres = null
            )
        }
    }
}