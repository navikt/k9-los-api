package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

class Oppgavetyper(
    val område: String,
    val definisjonskilde: String,
    val oppgavetyper: Set<Oppgavetype>
) {
    fun finnForskjell() {
        TODO("Not yet implemented")
    }
}