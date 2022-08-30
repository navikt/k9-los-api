package no.nav.k9.domene.lager.oppgave.v3.oppgave

class OppgaveFeltverdi(
    val oppgavefeltId: Long,
    val område: String? = null,
    val nøkkel: String,
    val verdi: String
)