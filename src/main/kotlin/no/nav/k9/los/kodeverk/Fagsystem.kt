package no.nav.k9.los.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Fagsystem(override val kode: String, override val kodeverk: String, override val navn: String): Kodeverdi {
    K9SAK("K9SAK", "FAGSYSTEM", "K9-sak"),
    K9TILBAKE("K9TILBAKE", "FAGSYSTEM", "K9-tilbake"),
    K9KLAGE("K9KLAGE", "FAGSYSTEM", "K9-klage"),
    PUNSJ("PUNSJ", "FAGSYSTEM", "K9-punsj");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): Fagsystem {
            val kode = TempAvledeKode.getVerdi(o)
            return values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}

