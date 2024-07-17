package no.nav.k9.los.nyoppgavestyring.query.dto.query

/**
 * Spørring som kan søke på oppgaver som oppfyller gitte filtre.
 */
data class OppgaveQuery(
    val filtere: List<Oppgavefilter>,
    val select: List<SelectFelt> = listOf(),
    val order: List<OrderFelt> = listOf(),
) {

    constructor() : this(listOf(), listOf(), listOf())

    constructor(filtere: List<Oppgavefilter>) : this(filtere, listOf(), listOf())

    constructor(filtere: List<Oppgavefilter>, order: List<OrderFelt>) : this(filtere, listOf(), order)
}