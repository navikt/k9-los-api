package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.slf4j.LoggerFactory

class OppgaveV3Tjeneste(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository,
    private val transactionalManager: TransactionalManager
) {

    private val log = LoggerFactory.getLogger(OppgaveV3Tjeneste::class.java)

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

        val utledeteFelter = mutableListOf<OppgaveFeltverdi>()
        oppgavetype.oppgavefelter
            .filter { oppgavefelt -> oppgavefelt.feltutleder != null }
            .forEach { oppgavefelt ->
                val utledetFeltverdi = oppgavefelt.feltutleder!!.utled(innkommendeOppgave, aktivOppgaveVersjon)
                if (utledetFeltverdi != null) {
                    utledeteFelter.add(utledetFeltverdi)
                }
            }

        innkommendeOppgave = OppgaveV3(innkommendeOppgave, innkommendeOppgave.felter.plus(utledeteFelter))

        innkommendeOppgave.valider()
        //oppgavetype.validerInnkommendeOppgave(oppgaveDto)

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

    fun oppdaterEkstisterendeOppgaveversjon(oppgaveDto: OppgaveDto, tx: TransactionalSession) {
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(
            område = oppgaveDto.område,
            eksternId = oppgaveDto.type,
            tx = tx
        )

        val forrigeOppgaveversjon =
            oppgaveV3Repository.hentOppgaveversjonenFør(oppgaveDto.id, oppgaveDto.versjon, oppgavetype, tx)
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

        oppgaveV3Repository.slettFeltverdier(
            eksternId = oppgaveDto.id,
            eksternVersjon = oppgaveDto.versjon,
            tx = tx
        )

        oppgaveV3Repository.lagreFeltverdier(
            eksternId = oppgaveDto.id,
            eksternVersjon = oppgaveDto.versjon,
            oppgaveFeltverdier = innkommendeOppgave.felter,
            tx = tx
        )

        oppgaveV3Repository.oppdaterReservasjonsnøkkel(oppgaveDto.id, oppgaveDto.versjon, oppgaveDto.reservasjonsnøkkel, tx)
    }

    fun nyEksternversjon(oppgaveDto: OppgaveDto, tx: TransactionalSession): Boolean {
        return !oppgaveV3Repository.finnesFraFør(tx, oppgaveDto.id, oppgaveDto.versjon)
    }

    fun tellAntall(): Pair<Long, Long> {
        return oppgaveV3Repository.tellAntall()
    }

    fun destruktivSlettAvAlleOppgaveData() {
        log.info("trunkerer oppgavedata")
        oppgaveV3Repository.slettOppgaverOgFelter()
        log.info("oppgavedata trunkert")
    }
}