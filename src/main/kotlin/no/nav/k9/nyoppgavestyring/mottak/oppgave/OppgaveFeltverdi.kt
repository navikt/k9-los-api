package no.nav.k9.nyoppgavestyring.mottak.oppgave

import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt

class OppgaveFeltverdi(
    val id: Long? = null,
    val oppgavefelt: Oppgavefelt,
    val verdi: String
)