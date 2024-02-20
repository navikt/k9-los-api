package no.nav.k9.los.nyoppgavestyring.ko.dto

import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import java.time.LocalDateTime

class OppgaveKoListeelement(
    val id: Long,
    val tittel: String,
    val query: OppgaveQuery,
    val sistEndret: LocalDateTime?,
    val antallOppgaver: Long,
    val antallSaksbehandlere: Int,
)