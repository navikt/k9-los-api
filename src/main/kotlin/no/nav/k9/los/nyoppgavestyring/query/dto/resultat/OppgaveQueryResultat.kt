package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveId
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId

sealed interface OppgaveQueryResultat {
    data class AntallResultat(val antall: Long) : OppgaveQueryResultat
    data class SelectResultat(val rader: List<OppgaveResultat>) : OppgaveQueryResultat
    data class AggregertResultat(val rader: List<AggregertQueryResultat>) : OppgaveQueryResultat
    data class EksternIdResultat(val ider: List<EksternOppgaveId>) : OppgaveQueryResultat
    data class OppgaveIdResultat(val ider: List<OppgaveId>) : OppgaveQueryResultat
}
