package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import no.nav.k9.los.domene.lager.oppgave.v2.equalsWithPrecision
import java.time.LocalDateTime

class ReservasjonV3(
    val id: Long? = null,
    val saksbehandlerEpost: String,
    val reservasjonsnøkkel: String,
    val gyldigFra: LocalDateTime,
    val gyldigTil: LocalDateTime,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReservasjonV3

        if (saksbehandlerEpost != other.saksbehandlerEpost) return false
        if (reservasjonsnøkkel != other.reservasjonsnøkkel) return false
        if (!gyldigFra.equalsWithPrecision(other.gyldigFra, 10)) return false
        return gyldigTil.equalsWithPrecision(other.gyldigTil, 10)
    }

    override fun hashCode(): Int {
        var result = saksbehandlerEpost.hashCode()
        result = 31 * result + reservasjonsnøkkel.hashCode()
        result = 31 * result + gyldigFra.hashCode()
        result = 31 * result + gyldigTil.hashCode()
        return result
    }
}
