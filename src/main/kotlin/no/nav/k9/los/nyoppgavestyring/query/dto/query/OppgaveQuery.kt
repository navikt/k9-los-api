package no.nav.k9.los.nyoppgavestyring.query.dto.query

/**
 * Spørring som kan søke på oppgaver som oppfyller gitte filtre.
 */
data class OppgaveQuery(
    val filtere: List<Oppgavefilter>,
    val select: List<SelectFelt> = listOf(),
    val order: List<OrderFelt> = listOf(),
    val limit: Int = -1
) {

    constructor() : this(listOf(), listOf(), listOf(), 10);

    constructor(filtere: List<Oppgavefilter>) : this(filtere, listOf(), listOf(), 10);

    constructor(filtere: List<Oppgavefilter>, order: List<OrderFelt>) : this(filtere, listOf(), order, 10)
}