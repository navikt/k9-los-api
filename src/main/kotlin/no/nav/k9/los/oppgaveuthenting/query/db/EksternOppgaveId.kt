package no.nav.k9.los.oppgaveuthenting.query.db

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class EksternOppgaveId (
    val område: Områder,
    val eksternId: String
)
