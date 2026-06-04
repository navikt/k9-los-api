package no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.feltdefinisjon

enum class Datatype(val kode: String) {
    INTEGER("Integer"),
    DURATION("Duration"),
    TIMESTAMP("Timestamp"),
    BOOLEAN("boolean"),
    STRING("String"),
    PERIODE("Periode");

    companion object {
        fun fraKode(kode: String): Datatype {
            return entries.first { it.kode == kode }
        }
    }
}
