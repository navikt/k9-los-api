package no.nav.k9.los.nyoppgavestyring.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator


enum class EgenAnsatt(val kode: String, val beskrivelse: String) {
    JA("JA", "Egen ansatt"),
    NEI("NEI", "Ikke egen ansatt");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): EgenAnsatt {
            return EgenAnsatt.entries.find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}
