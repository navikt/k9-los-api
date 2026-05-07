package no.nav.k9.los.nyoppgavestyring.forvaltning

import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery

data class OppgaveKøMedQuery(val id: Long, val tittel: String, val oppgaveQuery: OppgaveQuery)