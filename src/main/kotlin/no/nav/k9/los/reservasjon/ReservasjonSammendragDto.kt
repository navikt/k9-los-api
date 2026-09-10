package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import java.time.LocalDateTime

data class ReservasjonSammendragDto(
    val oppgaver: List<OppgaveSammendragDto>,
    val reservasjonsnøkkel: String,
    val reservertAvNavn: String?,
    val reservertAvIdent: String,
    val reservertAvEpost: String,
    val reservertAvId: Long,
    val kommentar: String,
    val reservertFra: LocalDateTime,
    val reservertTil: LocalDateTime,
    val endretAvNavn: String?,
) {
    constructor(
        reservasjon: ReservasjonV3,
        oppgaver: List<OppgaveSammendragDto>,
        reservertAv: Saksbehandler,
        endretAvNavn: String?,
    ) : this(
        oppgaver = oppgaver,
        reservasjonsnøkkel = reservasjon.reservasjonsnøkkel,
        reservertAvNavn = reservertAv.navn,
        reservertAvIdent = reservertAv.navident!!,
        reservertAvEpost = reservertAv.epost,
        reservertAvId = reservertAv.id!!,
        kommentar = reservasjon.kommentar ?: "",
        reservertFra = reservasjon.gyldigFra,
        reservertTil = reservasjon.gyldigTil,
        endretAvNavn = endretAvNavn,
    )
}
