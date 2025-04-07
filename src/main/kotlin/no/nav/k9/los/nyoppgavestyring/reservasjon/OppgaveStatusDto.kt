package no.nav.k9.los.nyoppgavestyring.reservasjon

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import java.time.LocalDateTime

//ingen auditlogg på denne, siden den ikke inneholder personidentifiserende informasjon, bortsett fra saksbehandlers ident
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
    constructor(reservasjonV3: ReservasjonV3, innloggetBruker: Saksbehandler, saksbehandlerSomHarReservasjon: Saksbehandler) : this (
    erReservert = true,
    reservertTilTidspunkt = reservasjonV3.gyldigTil,
    erReservertAvInnloggetBruker = reservasjonV3.reservertAv == innloggetBruker.id!!,
    reservertAv = saksbehandlerSomHarReservasjon.brukerIdent,
    reservertAvNavn = saksbehandlerSomHarReservasjon.navn,
    flyttetReservasjon = null,
    kanOverstyres = reservasjonV3.reservertAv != innloggetBruker.id!!
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
