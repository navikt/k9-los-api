package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

data class GruppertOppgaveAntall(
    val grupperingsverdier: List<Oppgavefeltverdi>,
    val antall: Long
)
