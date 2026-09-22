package no.nav.k9.los.oppgavedefinisjon.omraade

/**
 * Registeret over gyldige område-eksternIder i Los.
 *
 * Et område er et subdomene for oppgaver. Radene i tabellen `omrade` opprettes av
 * Områdesetup og tilsvarende klasser ved oppstart. Et nytt område legges til i denne
 * enumen i samme commit som oppsettet for området — for en gitt kompilering er derfor
 * alle områder kjent.
 *
 * Enumen holder kun eksternId. Trenger du det persisterte [Område] med database-id,
 * hentes det via OmrådeRepository.hentOmråde(område).
 *
 * [urlSegment] er området sitt prefiks i API-URLene (f.eks. `k9/los/api`).
 */
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