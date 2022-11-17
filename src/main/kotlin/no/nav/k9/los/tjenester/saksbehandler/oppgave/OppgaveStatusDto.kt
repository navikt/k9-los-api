package no.nav.k9.los.tjenester.saksbehandler.oppgave

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
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
)

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Beskjed(val kode: String) {
        BESLUTTET_AV_DEG("BESLUTTET_AV_DEG");

        companion object {
                @JsonCreator
                @JvmStatic
                fun fraKode(navn: String): Beskjed = values().find { it.kode == navn }!!
        }
}
