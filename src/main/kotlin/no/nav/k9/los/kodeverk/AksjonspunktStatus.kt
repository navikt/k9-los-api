package no.nav.k9.los.kodeverk

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class AksjonspunktStatus(@JsonValue val kode: String, val navn: String) {
    AVBRUTT("AVBR", "Avbrutt"),
    OPPRETTET("OPPR", "Opprettet"),
    UTFØRT("UTFO", "Utført");

    companion object {
        private val KODER = values().associateBy { it.kode }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fraKode(kode: String): AksjonspunktStatus {
            return KODER[kode] ?: throw IllegalStateException("Kjenner ikke igjen koden=$kode")
        }
    }
}

