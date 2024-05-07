package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import org.slf4j.LoggerFactory

class OppgaveV3Tjeneste(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository,
    private val reservasjonTjeneste: ReservasjonV3Tjeneste
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

        oppgaveV3Repository.nyOppgaveversjon(innkommendeOppgave, tx)

        /* TODO: Denne løsningen var litt for enkel. Vi må ha en variant som ser på reservasjonsnøkkel på tvers av _beslutter postfix.
             Evt få besluttermekanismen som first class citizen på oppgavemodellen
        if (innkommendeOppgave.status == Oppgavestatus.LUKKET) {
            val oppgaverIderFornøkkel =
                oppgaveV3Repository.hentOppgaveEksternIderForReservasjonsnøkkel(
                    innkommendeOppgave.reservasjonsnøkkel,
                    tx
                )
            val oppgaver = oppgaverIderFornøkkel.mapNotNull { eksternId ->
                oppgaveV3Repository.hentAktivOppgave(eksternId, innkommendeOppgave.oppgavetype, tx)
            }
            if (!oppgaver.any { oppgave -> oppgave.status == Oppgavestatus.AAPEN || oppgave.status == Oppgavestatus.VENTER}) { //TODO: hvorfor må oppgave nullsafes her??
                    reservasjonTjeneste.annullerReservasjonHvisFinnes(innkommendeOppgave.reservasjonsnøkkel, "Alle oppgaver på nøkkel er avsluttet. Annulleres maskinelt", null)
            }
        }

         */

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

    fun oppdaterEksisterendeOppgaveversjon(oppgaveDto: OppgaveDto, eventNr: Long, tx: TransactionalSession) {
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(
            område = oppgaveDto.område,
            eksternId = oppgaveDto.type,
            tx = tx
        )

        val forrigeOppgaveversjon = if (eventNr > 0) {
            oppgaveV3Repository.hentOppgaveversjonenFør(oppgaveDto.id, eventNr, oppgavetype, tx)
        } else { null }
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

        //historikkvasktjenesten skal sørge for at oppgaven med internVersjon = eventNr faktisk eksisterer
        oppgaveV3Repository.slettFeltverdier(
            eksternId = innkommendeOppgave.eksternId,
            internVersjon = eventNr,
            tx = tx
        )

        oppgaveV3Repository.lagreFeltverdier(
            eksternId = innkommendeOppgave.eksternId,
            internVersjon = eventNr,
            oppgaveFeltverdier = innkommendeOppgave.felter,
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
        val (_, versjon) = oppgaveV3Repository.hentOppgaveIdOgHøyesteInternversjon(tx, oppgaveEksternId, opppgaveTypeEksternId, områdeEksternId)
        return versjon
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