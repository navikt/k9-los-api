package no.nav.k9.nyoppgavestyring.oppgave

import kotliquery.TransactionalSession
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.nyoppgavestyring.adaptere.statistikkadapter.OppgaveV3TilBehandlingAdapter
import no.nav.k9.nyoppgavestyring.omraade.OmrådeRepository
import no.nav.k9.nyoppgavestyring.oppgavetype.OppgavetypeRepository
import no.nav.k9.statistikk.kontrakter.Behandling

class OppgaveV3Tjeneste(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository,
    private val transactionalManager: TransactionalManager,
    private val oppgaveV3TilBehandlingAdapter: OppgaveV3TilBehandlingAdapter
) {

    fun sjekkDuplikatOgProsesser(dto: OppgaveDto) {
        transactionalManager.transaction { tx ->
            if (!idempotensMatch(dto, tx)) {
                oppdater(dto, tx)
                //val behandlingEvent = byggOppgavestatistikkForBehandling(dto.id, tx)
                //sendEvent(behandlingEvent)
                //val afbasdgf = byggOppgavestatistikkForSak(dto.id)
            }
        }
    }

    private fun byggOppgavestatistikkForBehandling(id: String, tx: TransactionalSession): Behandling {
        //oppgaveV3TilBehandlingAdapter.lagBehandling()
        TODO()
    }

    fun oppdater(oppgaveDto: OppgaveDto, tx: TransactionalSession) {
        val område = områdeRepository.hentOmråde(oppgaveDto.område, tx)
        val oppgavetyper = oppgavetypeRepository.hent(område, tx) //TODO: cache denne? Invalideres av post-kall på oppgavetype eller feltdefinisjon
        val oppgavetype = oppgavetyper.oppgavetyper.find { it.eksternId.equals(oppgaveDto.type) }
            ?: throw IllegalArgumentException("Kan ikke legge til oppgave på en oppgavetype som ikke er definert")

        oppgavetype.valider(oppgaveDto)

        if (!oppgaveV3Repository.idempotensMatch(tx, oppgaveDto.id, oppgaveDto.versjon)) {
            val innkommendeOppgave = OppgaveV3(oppgaveDto, oppgavetype)
            oppgaveV3Repository.lagre(innkommendeOppgave, tx)
        }
    }

    fun idempotensMatch(oppgaveDto: OppgaveDto, tx: TransactionalSession): Boolean {
        return oppgaveV3Repository.idempotensMatch(tx, oppgaveDto.id, oppgaveDto.versjon)
    }

}