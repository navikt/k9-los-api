package no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgave

import com.fasterxml.jackson.annotation.JsonCreator

enum class Oppgavestatus(val kode: String, val visningsnavn: String) {
    AAPEN("AAPEN", "Åpen"),
    VENTER("VENTER", "Venter"),
    LUKKET("LUKKET", "Lukket");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): Oppgavestatus {
            return Oppgavestatus.values().find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}