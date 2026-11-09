package no.nav.k9.los.tjenester.mock

import java.util.UUID

internal data class Testoppgave(
    val id: UUID,
    val saksnummer: String,
    val oppgaveEksternId: String,
    val oppgaveTypeEksternId: String,
)