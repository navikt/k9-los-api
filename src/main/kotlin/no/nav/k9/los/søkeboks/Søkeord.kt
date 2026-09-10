package no.nav.k9.los.søkeboks

/**
 * Et søkeord klassifisert etter hva det ser ut som.
 *
 * Klassifiseringen er områdeuavhengig: den handler om formatet på det brukeren skrev inn,
 * ikke om hvilke felt et område har. Hvorvidt et område faktisk støtter å søke på en gitt
 * variant avgjøres av [Oppgavesøk.lagQuery], som returnerer null når varianten ikke
 * kan besvares.
 *
 * Nye varianter her vil bryte kompileringen i hver [Oppgavesøk], som er meningen.
 */
sealed interface Søkeord {
    /** Fødselsnummer, sammen med aktørId-ene PDL kjenner for personen. */
    data class Person(val fnr: String, val aktørIder: List<String>) : Søkeord

    data class Journalpost(val journalpostId: String) : Søkeord

    data class Sak(val saksnummer: String) : Søkeord
}
