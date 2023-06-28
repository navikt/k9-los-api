package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import no.nav.k9.los.domene.modell.Saksbehandler
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ReservasjonV3 (
    val id: Long? = null,
    val reservertAv: Long,
    val reservasjonsnøkkel: String,
    val annullertFørUtløp: Boolean = false,
    gyldigFra: LocalDateTime,
    gyldigTil: LocalDateTime,
) {
    val gyldigFra = gyldigFra.truncatedTo(ChronoUnit.MICROS)
    val gyldigTil = gyldigTil.truncatedTo(ChronoUnit.MICROS)

    constructor(saksbehandler: Saksbehandler, taReservasjonDto: TaReservasjonDto) : this(
        reservertAv = saksbehandler.id!!,
        reservasjonsnøkkel = taReservasjonDto.reservasjonsnøkkel,
        gyldigFra = taReservasjonDto.gyldigFra,
        gyldigTil = taReservasjonDto.gyldigTil,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReservasjonV3

        if (reservertAv != other.reservertAv) return false
        if (reservasjonsnøkkel != other.reservasjonsnøkkel) return false
        if (annullertFørUtløp != other.annullertFørUtløp) return false

        if (!gyldigFra.equals(other.gyldigFra)) return false
        return gyldigTil.equals(other.gyldigTil)
    }

    override fun hashCode(): Int {
        var result = reservertAv.hashCode()
        result = 31 * result + reservasjonsnøkkel.hashCode()
        result = 31 * result + annullertFørUtløp.hashCode()
        result = 31 * result + gyldigFra.hashCode()
        result = 31 * result + gyldigTil.hashCode()
        return result
    }
}