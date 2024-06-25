package no.nav.k9.los.nyoppgavestyring.reservasjon

import java.time.LocalDateTime

class ReservasjonV3MedEndring(
    val id: Long,
    val reservertAv: Long,
    val reservasjonsnøkkel: String,
    val annullertFørUtløp: Boolean = false,
    val kommentar: String?,
    val gyldigFra: LocalDateTime,
    val gyldigTil: LocalDateTime,
    val reservasjonOpprettet: LocalDateTime,
    val sist_endret: LocalDateTime,

    val endringId: Long?,
    val annullertReservasjonId: Long?,
    val nyReservasjonId: Long?,
    val endretAv: Long?,
    val endringOpprettet: LocalDateTime?,
)