package no.nav.k9.tjenester.saksbehandler.oppgave

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.domene.lager.oppgave.Kodeverdi
import java.time.LocalDateTime

class OppgaveStatusDto(
    val erReservert: Boolean,
    val reservertTilTidspunkt: LocalDateTime?,
    val erReservertAvInnloggetBruker: Boolean,
    val reservertAv: String?,
    val reservertAvNavn: String?,
    val flyttetReservasjon: FlyttetReservasjonDto?,
    val kanOverstyres: Boolean? = false,
    val beskjed: String? = null
)

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Beskjed constructor(override val kode: String, override val navn: String) : Kodeverdi {
    BESLUTTET_AV_DEG("BESLUTTET_AV_DEG", "Besluttet av deg") {


        override val kodeverk = "BESKJED_TYPE"

    };

    companion object {
        @JsonCreator
        @JvmStatic
        fun fraKode(navn: String): Beskjed = values().find { it.kode == navn }!!
    }
}
