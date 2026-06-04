package no.nav.k9.los.nyoppgavestyring.lagretsok

import no.nav.k9.los.nyoppgavestyring.uthenting.query.dto.query.OppgaveQuery

data class NyttLagretSøkRequest(val tittel: String, val query: OppgaveQuery)
