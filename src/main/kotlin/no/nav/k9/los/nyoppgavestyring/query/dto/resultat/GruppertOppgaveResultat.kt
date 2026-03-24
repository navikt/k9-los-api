package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

data class GruppertOppgaveResultat(
    val grupperingsverdier: List<Oppgavefeltverdi>,
    val aggregeringer: List<Aggregertverdi>,
)
