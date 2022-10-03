package no.nav.k9.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk.OppgavestatistikkTjeneste
import no.nav.k9.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.slf4j.LoggerFactory

class OppgaveV3Tjeneste(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository,
    private val transactionalManager: TransactionalManager,
    private val oppgavestatistikkTjeneste: OppgavestatistikkTjeneste
) {

    private val log = LoggerFactory.getLogger(OppgaveV3Tjeneste::class.java)

    fun sjekkDuplikatOgProsesser(dto: OppgaveDto): OppgaveV3? {
        var oppgave: OppgaveV3? = null
        transactionalManager.transaction { tx ->
            val duplikatsjekk = System.currentTimeMillis()
            val skalOppdatere = skalOppdatere(dto, tx)
            log.info("Duplikatsjekk av oppgave med eksternId: ${dto.id}, tidsbruk: ${System.currentTimeMillis() - duplikatsjekk}")
            if (skalOppdatere) {
                val startOppdatering = System.currentTimeMillis()
                oppgave = oppdater(dto, tx)
                log.info("Lagret oppgave med eksternId: ${dto.id}, tidsbruk: ${System.currentTimeMillis() - startOppdatering}")

                oppgavestatistikkTjeneste.sendStatistikk(dto.id, tx)
            }
        }
        return oppgave
    }

    private fun oppdater(oppgaveDto: OppgaveDto, tx: TransactionalSession): OppgaveV3 {
        val område = områdeRepository.hentOmråde(oppgaveDto.område, tx)
        val oppgavetyper = oppgavetypeRepository.hent(område, tx) //TODO: cache denne? Invalideres av post-kall på oppgavetype eller feltdefinisjon
        val oppgavetype = oppgavetyper.oppgavetyper.find { it.eksternId.equals(oppgaveDto.type) }
            ?: throw IllegalArgumentException("Kan ikke legge til oppgave på en oppgavetype som ikke er definert")

        oppgavetype.valider(oppgaveDto)

        val innkommendeOppgave = OppgaveV3(oppgaveDto, oppgavetype)
        oppgaveV3Repository.lagre(innkommendeOppgave, tx)

        return innkommendeOppgave
    }

    fun skalOppdatere(oppgaveDto: OppgaveDto, tx: TransactionalSession): Boolean {
        return !oppgaveV3Repository.finnesFraFør(tx, oppgaveDto.id, oppgaveDto.versjon)
    }

    fun tellAntall(): Pair<Long, Long> {
        return oppgaveV3Repository.tellAntall()
    }

}