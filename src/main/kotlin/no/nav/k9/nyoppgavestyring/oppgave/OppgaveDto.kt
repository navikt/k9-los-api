package no.nav.k9.nyoppgavestyring.oppgave

data class OppgaveDto(
    val id: String,
    val versjon: String,
    val område: String,
    val kildeområde: String,
    val type: String,
    val status: String,
    val feltverdier: List<OppgaveFeltverdiDto>
)
