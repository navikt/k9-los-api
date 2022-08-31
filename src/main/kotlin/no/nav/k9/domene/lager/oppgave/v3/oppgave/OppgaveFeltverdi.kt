package no.nav.k9.domene.lager.oppgave.v3.oppgave

import no.nav.k9.domene.lager.oppgave.v3.oppgavetype.Oppgavefelt

class OppgaveFeltverdi(
    val id: Long? = null,
    val oppgavefelt: Oppgavefelt,
    val verdi: String
)