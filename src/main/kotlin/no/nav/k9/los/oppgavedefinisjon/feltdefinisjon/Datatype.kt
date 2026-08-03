package no.nav.k9.los.oppgavedefinisjon.feltdefinisjon

enum class Datatype(val kode: String) {
    INTEGER("Integer"),
    DURATION("Duration"),
    TIMESTAMP("Timestamp"),
    BOOLEAN("boolean"),
    STRING("String"),
    PERIODE("Periode");

    companion object {
        fun fraKode(kode: String): Datatype {
            return entries.firstOrNull { it.kode == kode }
                ?: throw NoSuchElementException("Ukjent Datatype-kode: '$kode'. Gyldige koder: ${entries.map { it.kode }}")
        }
    }
}
