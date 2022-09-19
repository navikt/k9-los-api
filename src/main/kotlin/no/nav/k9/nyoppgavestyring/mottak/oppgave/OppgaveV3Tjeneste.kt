package no.nav.k9.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk.OppgavestatistikkTjeneste
import no.nav.k9.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository

class OppgaveV3Tjeneste(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository,
    private val transactionalManager: TransactionalManager,
    private val oppgavestatistikkTjeneste: OppgavestatistikkTjeneste
) {

    fun sjekkDuplikatOgProsesser(dto: OppgaveDto) {
        transactionalManager.transaction { tx ->
            if (!idempotensMatch(dto, tx)) {
                oppdater(dto, tx)
                oppgavestatistikkTjeneste.sendStatistikk(dto.id, tx)
            }
        }
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