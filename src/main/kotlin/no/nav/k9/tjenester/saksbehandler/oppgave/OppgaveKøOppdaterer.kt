package no.nav.k9.tjenester.saksbehandler.oppgave

import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.ReservasjonRepository
import java.util.*

class OppgaveKøOppdaterer(
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val reservasjonRepository: ReservasjonRepository,
) {

    suspend fun oppdater(oppgaveId: UUID) {
        val oppgave = oppgaveRepository.hent(oppgaveId)
        for (oppgavekø in oppgaveKøRepository.hent()) {
            oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø.id, listOf(oppgave), reservasjonRepository)
        }
    }
}