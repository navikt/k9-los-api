package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.domene.modell.Saksbehandler
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ReservasjonV3(
    val id: Long? = null,
    val reservertAv: Long,
    val reservasjonsnøkkel: String,
    val annullertFørUtløp: Boolean = false,
    val kommentar: String?,
    gyldigFra: LocalDateTime,
    gyldigTil: LocalDateTime,
    val endretAv: Long?,
) {
    val gyldigFra = gyldigFra.truncatedTo(ChronoUnit.MICROS)
    val gyldigTil = gyldigTil.truncatedTo(ChronoUnit.MICROS)

    fun copy(
        id: Long?,
        reservertAv: Long = this.reservertAv,
        reservasjonsnøkkel: String = this.reservasjonsnøkkel,
        annullertFørUtløp: Boolean = this.annullertFørUtløp,
        kommentar: String? = this.kommentar,
        gyldigFra: LocalDateTime = this.gyldigFra,
        gyldigTil: LocalDateTime = this.gyldigTil,
        endretAv: Long? = this.endretAv
    ): ReservasjonV3 {
        return ReservasjonV3(
            id,
            reservertAv,
            reservasjonsnøkkel,
            annullertFørUtløp,
            kommentar,
            gyldigFra,
            gyldigTil,
            endretAv
        )
    }

    constructor(
        saksbehandlerId: Long,
        reservasjonsnøkkel: String,
        kommentar: String,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        endretAv: Long?
    ) : this(
        reservertAv = saksbehandlerId,
        reservasjonsnøkkel = reservasjonsnøkkel,
        kommentar = kommentar,
        gyldigFra = gyldigFra,
        gyldigTil = gyldigTil,
        endretAv = endretAv
    )

    constructor(
        saksbehandler: Saksbehandler,
        reservasjonsnøkkel: String,
        kommentar: String,
        gyldigFra: LocalDateTime,
        gyldigTil: LocalDateTime,
        endretAv: Long?
    ) : this(
        reservertAv = saksbehandler.id!!,
        reservasjonsnøkkel = reservasjonsnøkkel,
        kommentar = kommentar,
        gyldigFra = gyldigFra,
        gyldigTil = gyldigTil,
        endretAv = endretAv
    )

    fun erAktiv(): Boolean {
        return !annullertFørUtløp && gyldigTil.isAfter(LocalDateTime.now())
    }

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

    override fun toString(): String {
        return """
            Reservasjon(
            id: $id,
            reservertAv: $reservertAv,
            reservasjonsnøkkel: ${Reservasjonsnøkkel(reservasjonsnøkkel)},
            annullertFørUtløp: $annullertFørUtløp,
            gyldigFra: $gyldigFra,
            gyldigTil: $gyldigTil)
        """.trimIndent()
    }
}