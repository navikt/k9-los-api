package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9FeltIder
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave

data class Oppgavetilstand(
    val saksnummer: String?,
    val journalpostId: String?,
    val eksternId: String,
    val status: String,
    val ytelseType: String,
    val frist: String?,
) {
    constructor(oppgave: Oppgave) : this(
        saksnummer = oppgave.hentVerdi(K9FeltIder.SAKSNUMMER),
        journalpostId = oppgave.hentVerdi(K9FeltIder.JOURNALPOST_ID),
        eksternId = oppgave.eksternId,
        status = oppgave.status,
        ytelseType = oppgave.hentVerdi(K9FeltIder.YTELSESTYPE)!!,
        frist = oppgave.hentVerdi(K9FeltIder.AKTIV_VENTEFRIST),
    )
}