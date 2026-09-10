package no.nav.k9.los.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class Enhet(val navn: String) {
    NASJONAL("NASJONAL");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(o: Any): Enhet {
            val navn = TempAvledeKode.getVerdi(o, "navn")
            return values().find { it.navn == navn } ?: throw IllegalStateException("Kjenner ikke igjen navnet=$navn")
        }
    }
}

