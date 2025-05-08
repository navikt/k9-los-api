package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository

class OppgaveV3Tjeneste(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val partisjonertOppgaveRepository: PartisjonertOppgaveRepository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository
) {

    fun sjekkDuplikatOgProsesser(dto: OppgaveDto, tx: TransactionalSession): OppgaveV3? {
        var oppgave: OppgaveV3? = null
        val skalOppdatere = nyEksternversjon(dto, tx)

        if (skalOppdatere) {
            oppgave = lagreNyOppgaveversjon(dto, tx)
        }
        return oppgave
    }

    private fun lagreNyOppgaveversjon(oppgaveDto: OppgaveDto, tx: TransactionalSession): OppgaveV3 {
        val område = områdeRepository.hentOmråde(oppgaveDto.område, tx)
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(
            område = område.eksternId,
            eksternId = oppgaveDto.type,
            tx = tx
        )

        val aktivOppgaveVersjon = oppgaveV3Repository.hentAktivOppgave(oppgaveDto.id, oppgavetype, tx)
        var innkommendeOppgave = OppgaveV3(oppgaveDto, oppgavetype)

        val utledeteFelter = oppgavetype.oppgavefelter
            .mapNotNull { oppgavefelt ->
                oppgavefelt.feltutleder?.utled(innkommendeOppgave, aktivOppgaveVersjon)
            }

        innkommendeOppgave = OppgaveV3(innkommendeOppgave, innkommendeOppgave.felter + utledeteFelter)

        innkommendeOppgave.valider()

        oppgaveV3Repository.nyOppgaveversjon(innkommendeOppgave, tx)

        return innkommendeOppgave
    }

    fun hentEksternIdForOppgaverMedStatus(oppgavetypeEksternId: String, områdeEksternId: String, oppgavestatus: Oppgavestatus, tx: TransactionalSession) : List<String> {
        tx.run {
            val område = områdeRepository.hentOmråde(områdeEksternId, tx)
            val oppgavetype = oppgavetypeRepository.hentOppgavetype(område, oppgavetypeEksternId, tx)
            return oppgaveV3Repository.hentEksternIdForOppgaverMedStatus(oppgavetype, område, oppgavestatus, tx)
        }
    }

    fun hentAktivOppgave(eksternId: String, oppgavetypeEksternId: String, områdeEksternId: String, tx: TransactionalSession) : OppgaveV3 {
        tx.run {
            val område = områdeRepository.hentOmråde(områdeEksternId, tx)
            val oppgavetype = oppgavetypeRepository.hentOppgavetype(område, oppgavetypeEksternId, tx)
            return oppgaveV3Repository.hentAktivOppgave(eksternId, oppgavetype, tx)!!
        }
    }

    fun hentOppgaveversjon(
        område: String,
        eksternId: String,
        eksternVersjon: String,
        tx: TransactionalSession
    ): OppgaveV3 {
        return oppgaveV3Repository.hentOppgaveversjon(
            område = områdeRepository.hentOmråde(område, tx),
            eksternId = eksternId,
            eksternVersjon = eksternVersjon,
            tx = tx
        )
    }

    fun ajourholdOppgave(innkommendeOppgave: OppgaveV3, internVersjon: Long, tx: TransactionalSession) {
        AktivOppgaveRepository.ajourholdAktivOppgave(innkommendeOppgave, internVersjon, tx)
        partisjonertOppgaveRepository.ajourhold(innkommendeOppgave, tx)
    }

    fun slettAktivOppgave(innkommendeOppgave: OppgaveV3, tx: TransactionalSession){
        AktivOppgaveRepository.slettAktivOppgave(tx, innkommendeOppgave)
    }


    fun utledEksisterendeOppgaveversjon(oppgaveDto: OppgaveDto, eventNr: Long, tx: TransactionalSession) : OppgaveV3 {
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(
            område = oppgaveDto.område,
            eksternId = oppgaveDto.type,
            tx = tx
        )

        val forrigeOppgaveversjon = if (eventNr > 0) {
            oppgaveV3Repository.hentOppgaveversjonenFør(oppgaveDto.id, eventNr, oppgavetype, tx)
        } else {
            null
        }
        var innkommendeOppgave = OppgaveV3(oppgaveDto = oppgaveDto, oppgavetype = oppgavetype)

        val utledeteFelter = mutableListOf<OppgaveFeltverdi>()
        oppgavetype.oppgavefelter
            .filter { oppgavefelt -> oppgavefelt.feltutleder != null }
            .forEach { oppgavefelt ->
                val utledetFeltverdi = oppgavefelt.feltutleder!!.utled(innkommendeOppgave, forrigeOppgaveversjon)
                if (utledetFeltverdi != null) {
                    utledeteFelter.add(utledetFeltverdi)
                }
            }

        innkommendeOppgave = OppgaveV3(innkommendeOppgave, innkommendeOppgave.felter.plus(utledeteFelter))
        innkommendeOppgave.valider()
        return innkommendeOppgave
    }

    fun oppdaterEksisterendeOppgaveversjon(innkommendeOppgave: OppgaveV3, eventNr: Long, tx: TransactionalSession)  {

        //historikkvasktjenesten skal sørge for at oppgaven med internVersjon = eventNr faktisk eksisterer
        oppgaveV3Repository.slettFeltverdier(
            eksternId = innkommendeOppgave.eksternId,
            internVersjon = eventNr,
            tx = tx
        )

        oppgaveV3Repository.lagreFeltverdierForDatavask(
            eksternId = innkommendeOppgave.eksternId,
            internVersjon = eventNr,
            oppgaveFeltverdier = innkommendeOppgave.felter,
            oppgavestatus = innkommendeOppgave.status,
            tx = tx
        )

        oppgaveV3Repository.oppdaterReservasjonsnøkkelStatusOgEksternVersjon(
            eksternId = innkommendeOppgave.eksternId,
            eksternVersjon = innkommendeOppgave.eksternVersjon,
            status = innkommendeOppgave.status,
            internVersjon = eventNr,
            reservasjonsnokkel = innkommendeOppgave.reservasjonsnøkkel,
            tx = tx)
    }

    fun hentHøyesteInternVersjon(oppgaveEksternId: String, opppgaveTypeEksternId: String, områdeEksternId: String, tx: TransactionalSession): Long? {
        val (_, _, versjon) = oppgaveV3Repository.hentOppgaveIdStatusOgHøyesteInternversjon(tx, oppgaveEksternId, opppgaveTypeEksternId, områdeEksternId)
        return versjon
    }

    fun nyEksternversjon(oppgaveDto: OppgaveDto, tx: TransactionalSession): Boolean {
        return !oppgaveV3Repository.finnesFraFør(tx, oppgaveDto.id, oppgaveDto.versjon)
    }

    fun tellAntall(): Pair<Long, Long> {
        return oppgaveV3Repository.tellAntall()
    }
}