package no.nav.k9.los.forvaltning

import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery

data class OppgaveKøMedQuery(val id: Long, val tittel: String, val oppgaveQuery: OppgaveQuery)