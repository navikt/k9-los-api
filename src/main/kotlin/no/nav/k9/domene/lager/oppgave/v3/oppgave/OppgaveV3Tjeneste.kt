package no.nav.k9.domene.lager.oppgave.v3.oppgave

import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.domene.lager.oppgave.v3.omraade.OmrådeRepository
import no.nav.k9.domene.lager.oppgave.v3.oppgavetype.OppgavetypeRepository

class OppgaveV3Tjeneste(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository,
    private val transactionalManager: TransactionalManager
) {

    fun oppdater(oppgaveDto: OppgaveDto) {
        transactionalManager.transaction { tx ->
            val område = områdeRepository.hentOmråde(oppgaveDto.område, tx)
            val oppgavetyper = oppgavetypeRepository.hent(område, tx) //TODO: cache denne? Invalideres av post-kall på oppgavetype eller feltdefinisjon
            val oppgavetype = oppgavetyper.oppgavetyper.find { it.eksternId.equals(oppgaveDto.type) }
                ?: throw IllegalArgumentException("Kan ikke legge til oppgave på en oppgavetype som ikke er definert")

            oppgavetype.valider(oppgaveDto)
            val innkommendeOppgave = OppgaveV3(oppgaveDto, oppgavetype)

            oppgaveV3Repository.lagre(innkommendeOppgave, tx)
        }
    }

}