package no.nav.k9.domene.lager.oppgave.v3.oppgave

data class OppgaveDto(
    val id: String,
    val kildeområde: String,
    val kilde: String,
    val type: String,
    val status: String,
    val feltverdier: Set<OppgaveFeltverdiDto>
)
