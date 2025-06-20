package no.nav.k9.los.nyoppgavestyring.ko

import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import java.time.LocalDateTime

data class ReservasjonV3FraKøDto (
    val oppgaveNøkkelDto: OppgaveNøkkelDto,
    val reservasjonsnøkkel: String,
    val oppgavebehandlingsUrl: String?,
    val reservertAv: String,
    val reservertFra: LocalDateTime,
    val reservertTil: LocalDateTime?,
) {
    constructor(reservasjonV3: ReservasjonV3, oppgave: Oppgave, reservertAv: Saksbehandler) : this (
        oppgaveNøkkelDto = OppgaveNøkkelDto(oppgave),
        reservasjonsnøkkel = reservasjonV3.reservasjonsnøkkel,
        oppgavebehandlingsUrl = oppgave.getOppgaveBehandlingsurl(),
        reservertAv = reservertAv.epost,
        reservertFra = reservasjonV3.gyldigFra,
        reservertTil = reservasjonV3.gyldigTil
    )
}
