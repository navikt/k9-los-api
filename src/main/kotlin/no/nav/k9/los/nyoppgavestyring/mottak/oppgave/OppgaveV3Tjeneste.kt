package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.slf4j.LoggerFactory

class OppgaveV3Tjeneste(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository
) {

    private val log = LoggerFactory.getLogger(OppgaveV3Tjeneste::class.java)

    fun sjekkDuplikatOgProsesser(dto: OppgaveDto, tx: TransactionalSession): OppgaveV3? {
        var oppgave: OppgaveV3? = null
        val skalOppdatere = skalOppdatere(dto, tx)

        if (skalOppdatere) {
            oppgave = oppdater(dto, tx)
        }
        return oppgave
    }

    private fun oppdater(oppgaveDto: OppgaveDto, tx: TransactionalSession): OppgaveV3 {
        val område = områdeRepository.hentOmråde(oppgaveDto.område, tx)
        val oppgavetype =
            oppgavetypeRepository.hent(område, tx).oppgavetyper.find { it.eksternId.equals(oppgaveDto.type) }
                ?: throw IllegalArgumentException("Kan ikke legge til oppgave på en oppgavetype som ikke er definert: ${oppgaveDto.type}")


        val aktivOppgaveVersjon = oppgaveV3Repository.hentAktivOppgave(oppgaveDto.id, oppgavetype, tx)
        var innkommendeOppgave = OppgaveV3(oppgaveDto, oppgavetype)

        val utledeteFelter = mutableListOf<OppgaveFeltverdi>()

        oppgavetype.oppgavefelter
            .filter { oppgavefelt -> oppgavefelt.feltutleder != null }
            .forEach {
                oppgavefelt ->
                val utledetFeltverdi = oppgavefelt.feltutleder!!.utled(innkommendeOppgave, aktivOppgaveVersjon)
                if (utledetFeltverdi != null) {
                    utledeteFelter.add(utledetFeltverdi)
                }
            }

        innkommendeOppgave = OppgaveV3(innkommendeOppgave, innkommendeOppgave.felter.plus(utledeteFelter))

        innkommendeOppgave.valider()
        //oppgavetype.validerInnkommendeOppgave(oppgaveDto)

        oppgaveV3Repository.lagre(innkommendeOppgave, tx)

        return innkommendeOppgave
    }

    fun skalOppdatere(oppgaveDto: OppgaveDto, tx: TransactionalSession): Boolean {
        return !oppgaveV3Repository.finnesFraFør(tx, oppgaveDto.id, oppgaveDto.versjon)
    }

    fun tellAntall(): Pair<Long, Long> {
        return oppgaveV3Repository.tellAntall()
    }

    fun slettOppgaveData() {
        oppgaveV3Repository.slettOppgaverOgFelter()
    }

}