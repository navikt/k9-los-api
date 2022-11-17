package no.nav.k9.los.tjenester.saksbehandler.oppgave

import java.time.LocalDateTime
import java.util.*

data class BehandletOppgave(
        val behandlingId: Long?,
        val saksnummer: String,
        val journalpostId: String?,
        val eksternId: UUID,
        val personnummer: String?,
        val navn: String?,
        val system: String?,
        var timestamp: LocalDateTime = LocalDateTime.now())

