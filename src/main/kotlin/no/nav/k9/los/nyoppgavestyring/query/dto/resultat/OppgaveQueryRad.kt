package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

data class OppgaveQueryRad(
    val feltverdier: List<Oppgavefeltverdi>,
    val aggregeringer: List<Aggregertverdi> = emptyList(),
)
