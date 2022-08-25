package no.nav.k9.domene.lager.oppgave.v3.oppgave

class OppgaveV3(
    val id: String,
    val område: String,
    val type: String,
    val felter: Set<Felt>
)