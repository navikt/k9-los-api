package no.nav.k9.domene.lager.oppgave.v3.omraade

class Oppgavetype(
    val id: String,
    val offentlig: Boolean,
    val oppgavefelter: Set<Oppgavefelt>
)