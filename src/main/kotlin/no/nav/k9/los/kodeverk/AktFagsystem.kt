package no.nav.k9.los.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class AktFagsystem(override val kode: String, override val kodeverk: String, override val navn: String): Kodeverdi {
    UNGSAK("UNGSAK", "FAGSYSTEM", "Ung-sak"),
    UNGTILBAKE("UNGTILBAKE", "FAGSYSTEM", "Ung-tilbake");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): AktFagsystem {
            val kode = TempAvledeKode.getVerdi(o)
            return entries.find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}

