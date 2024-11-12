package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

enum class Datatype(val kode: String) {
    INTEGER("Integer"),
    DURATION("Duration"),
    TIMESTAMP("Timestamp"),
    BOOLEAN("boolean"),
    STRING("String");

    companion object {
        fun fraKode(kode: String): Datatype {
            return entries.first { it.kode == kode }
        }
    }
}