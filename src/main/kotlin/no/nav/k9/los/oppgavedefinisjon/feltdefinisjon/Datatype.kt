package no.nav.k9.los.oppgavedefinisjon.feltdefinisjon

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class Datatype(val kode: String) {
    INTEGER("Integer"),
    DURATION("Duration"),
    TIMESTAMP("Timestamp"),
    BOOLEAN("boolean"),
    STRING("String"),
    PERIODE("Periode");

    @JsonValue
    fun tilKode(): String = kode

    companion object {
        @JvmStatic
        @JsonCreator
        fun fraKode(kode: String): Datatype {
            return entries.firstOrNull { it.kode.equals(kode, ignoreCase = true) || it.name.equals(kode, ignoreCase = true) }
                ?: throw NoSuchElementException("Ukjent Datatype-kode: '$kode'. Gyldige koder: ${entries.map { it.kode }}")
        }
    }
}
