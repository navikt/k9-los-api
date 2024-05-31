package no.nav.k9.los.nyoppgavestyring.datainnlasting.feltdefinisjon

enum class Datatype(val kode: String) {
    INTEGER("Integer"),
    DOUBLE("Double"),
    DURATION("Duration"),
    TIMESTAMP("Timestamp"),
    BOOLEAN("boolean"),
    STRING("String");

    companion object {
        fun fraKode(kode: String): Datatype {
            return try {
                Datatype.valueOf(kode)
            } catch (e: IllegalArgumentException ) {
                STRING
            }
        }
    }
}