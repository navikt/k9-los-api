package no.nav.k9.los.nyoppgavestyring.query.dto.query

/**
 * Spørring som kan søke på oppgaver som oppfyller gitte filtre.
 */
class OppgaveQuery(
    val filtere: List<Oppgavefilter>,
    val select: List<SelectFelt> = listOf()
) {
    constructor(filtere: List<Oppgavefilter>) : this(filtere, listOf());
}