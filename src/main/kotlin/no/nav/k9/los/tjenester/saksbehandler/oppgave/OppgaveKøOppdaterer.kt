package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import java.util.*

class OppgaveKøOppdaterer(
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val reservasjonRepository: ReservasjonRepository,
) {

    suspend fun oppdater(oppgaveId: UUID) {
        val oppgave = oppgaveRepository.hent(oppgaveId)
        for (oppgavekø in oppgaveKøRepository.hentAlleInkluderKode6()) {
            oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø.id, listOf(oppgave), reservasjonRepository)
        }
    }
}