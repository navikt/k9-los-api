package no.nav.k9.los.reservasjon

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import java.time.LocalDateTime

class OppgaveStatusDto(
    val erReservert: Boolean,
    val reservertTilTidspunkt: LocalDateTime?,
    val erReservertAvInnloggetBruker: Boolean,
    val reservertAv: String?,
    val reservertAvNavn: String?,
    val flyttetReservasjon: FlyttetReservasjonDto?,
    val kanOverstyres: Boolean? = false,
    val beskjed: Beskjed? = null
) {
    constructor(reservasjonV3: ReservasjonV3, saksbehandlerSomHarReservasjon: Saksbehandler) : this(
        erReservert = true,
        reservertTilTidspunkt = reservasjonV3.gyldigTil,
        erReservertAvInnloggetBruker = true,
        reservertAv = saksbehandlerSomHarReservasjon.navident,
        reservertAvNavn = saksbehandlerSomHarReservasjon.navn,
        flyttetReservasjon = null,
        kanOverstyres = false
    )
}



@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Beskjed(val kode: String) {
        BESLUTTET_AV_DEG("BESLUTTET_AV_DEG");

        companion object {
                @JsonCreator
                @JvmStatic
                fun fraKode(navn: String): Beskjed = values().find { it.kode == navn }!!
        }
}

class FlyttetReservasjonDto(
    val tidspunkt: LocalDateTime,
    val uid: String,
    val navn: String,
    val begrunnelse: String
)
