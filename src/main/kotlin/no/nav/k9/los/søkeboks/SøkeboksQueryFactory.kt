package no.nav.k9.los.søkeboks

import no.nav.k9.los.oppgaveuthenting.Oppgave

interface SøkeboksQueryFactory {
    fun finnOppgaverForJournalpostId(søkeord: String) : List<Oppgave>?
    fun finnOppgaverForSaksnummer(søkeord: String) : List<Oppgave>?
    fun finnOppgaverForFnr(søkeord: String) : List<Oppgave>?
}