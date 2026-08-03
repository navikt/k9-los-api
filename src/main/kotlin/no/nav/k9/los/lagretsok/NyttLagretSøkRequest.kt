package no.nav.k9.los.lagretsok

import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery

data class NyttLagretSøkRequest(val tittel: String, val query: OppgaveQuery)
