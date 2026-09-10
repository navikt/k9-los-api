package no.nav.k9.los.oppgavedefinisjon.omraade

import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem

/**
 * Registeret over gyldige område-eksternIder i Los.
 *
 * Et område er et subdomene for oppgaver. Radene i tabellen `omrade` opprettes av
 * OmrådeSetup og tilsvarende klasser ved oppstart. Et nytt område legges til i denne
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
    AKTIVITETSPENGER("AKTIVITETSPENGER", "aktivitetspenger");

    companion object {
        fun fraEksternId(eksternId: String): Områder =
            entries.find { it.eksternId == eksternId }
                ?: throw IllegalArgumentException(
                    "Ukjent område: $eksternId. Gyldige områder: ${entries.map { it.eksternId }}"
                )

        fun erGyldig(eksternId: String): Boolean = entries.any { it.eksternId == eksternId }

        fun fraUrlSegment(urlSegment: String): Områder =
            entries.find { it.urlSegment == urlSegment }
                ?: throw IllegalArgumentException(
                    "Ukjent område i url: $urlSegment. Gyldige områder: ${entries.map { it.urlSegment }}"
                )

        /**
         * Området et fagsystem hører til.
         *
         * Bevisst uttømmende `when` uten else-gren: legges det til et nytt fagsystem, vil dette
         * ikke kompilere før området for det er bestemt. Det hindrer at nye fagsystemer stilltiende
         * havner på feil område.
         */
        fun fraFagsystem(fagsystem: Fagsystem): Områder = when (fagsystem) {
            Fagsystem.K9SAK,
            Fagsystem.K9TILBAKE,
            Fagsystem.K9KLAGE,
            Fagsystem.PUNSJ -> K9

            Fagsystem.UNGSAK -> TODO()
            Fagsystem.UNGTILBAKE -> TODO()
        }
    }
}





