package no.nav.k9.los.nyoppgavestyring.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator

enum class BeskyttelseType(val kode: String, val beskrivelse: String) {
    KODE6("KODE6", "Kode 6"),
    KODE7("KODE7", "Kode 7"),
    ORDINÆR("ORDINÆR", "Vanlig");

    companion object {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): BeskyttelseType {
            return BeskyttelseType.entries.find { it.kode == kode } ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}
