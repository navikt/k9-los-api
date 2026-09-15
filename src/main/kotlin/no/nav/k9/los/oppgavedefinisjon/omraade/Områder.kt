package no.nav.k9.los.oppgavedefinisjon.omraade

enum class Områder(val eksternId: String, val urlSegment: String) {
    K9("K9", "k9"),
    AKTIVITETSPENGER("AKTIVITETSPENGER", "akt");

    companion object {
        fun fraEksternId(eksternId: String): Områder =
            entries.find { it.eksternId == eksternId }
                ?: throw IllegalArgumentException(
                    "Ukjent område: $eksternId. Gyldige områder: ${entries.map { it.eksternId }}"
                )

        fun fraUrlSegment(urlSegment: String): Områder =
            entries.find { it.urlSegment == urlSegment }
                ?: throw IllegalArgumentException(
                    "Ukjent url segment for område: $urlSegment. Gyldige områder: ${entries.map { it.urlSegment }}"
                )
    }
}