package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import java.time.LocalDateTime

/**
 * Reservasjonen uten oppgavene den omfatter. Områdeagnostisk.
 */
data class ReservasjonsinfoDto(
    val reservasjonsnøkkel: String,
    val reservertAvIdent: String?,
    val reservertAvNavn: String?,
    val reservertAvEpost: String,
    val kommentar: String?,
    val reservertFra: LocalDateTime,
    val reservertTil: LocalDateTime,
    val endretAvNavn: String?,
) {
    constructor(reservasjon: ReservasjonV3, reservertAv: Saksbehandler, endretAvNavn: String?) : this(
        reservasjonsnøkkel = reservasjon.reservasjonsnøkkel,
        reservertAvIdent = reservertAv.navident,
        reservertAvNavn = reservertAv.navn,
        reservertAvEpost = reservertAv.epost,
        kommentar = reservasjon.kommentar,
        reservertFra = reservasjon.gyldigFra,
        reservertTil = reservasjon.gyldigTil,
        endretAvNavn = endretAvNavn,
    )
}

/**
 * Reservasjonen med oppgavene den omfatter. Oppgavene tolkes av området de tilhører,
 * via [no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder].
 */
data class ReservasjonMedOppgaverDto(
    val reservasjon: ReservasjonsinfoDto,
    val oppgaver: List<OppgaveSammendragDto>,
)
