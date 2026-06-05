package no.nav.k9.los.forvaltning

import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery

data class LagretSøkMedQuery(val id: Long, val tittel: String, val saksbehandlerEpost: String, val oppgaveQuery: OppgaveQuery)