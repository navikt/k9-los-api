package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

sealed interface OppgaveQueryResultat {
    data class AntallResultat(val antall: Long) : OppgaveQueryResultat
    data class SelectResultat(val rader: List<OppgaveResultat>) : OppgaveQueryResultat
    data class GruppertResultat(val rader: List<GruppertOppgaveResultat>) : OppgaveQueryResultat
}
