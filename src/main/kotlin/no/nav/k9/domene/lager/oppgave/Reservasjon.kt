package no.nav.k9.domene.lager.oppgave

import java.time.LocalDateTime
import java.util.*

data class Reservasjon(
    var reservertTil: LocalDateTime?,
    var reservertAv: String,
    var flyttetAv: String?,
    var flyttetTidspunkt: LocalDateTime?,
    var begrunnelse: String?,
    val oppgave: UUID
) {
    fun erAktiv(): Boolean {
        return reservertTil !=null && reservertTil!!.isAfter(LocalDateTime.now())
    }
    
    fun erAktiv(eventTid:LocalDateTime): Boolean {
        return reservertTil !=null && reservertTil!!.isAfter(eventTid)
    }

    fun hentFlyttet(): Flyttet? {
        return flyttetAv?.takeIf { it.isNotBlank() }?.let {
            Flyttet(
                flyttetAv = it,
                flyttetTidspunkt = flyttetTidspunkt ?: throw IllegalStateException("Flyttet uten å ha satt flyttetTidspunkt"),
                begrunnelse = begrunnelse ?: throw IllegalStateException("Flyttet uten å ha satt begrunnelse")
            )
        }
    }

    data class Flyttet(
        var flyttetAv: String,
        var flyttetTidspunkt: LocalDateTime,
        var begrunnelse: String,
    )
}
