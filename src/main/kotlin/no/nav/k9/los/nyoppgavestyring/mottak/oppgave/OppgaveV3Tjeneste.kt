package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import org.jetbrains.annotations.VisibleForTesting

class OppgaveV3Tjeneste(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository
) {


    fun sjekkDuplikatOgProsesser(innsending: NyOppgaveVersjonInnsending, tx: TransactionalSession): OppgaveV3? {
        when (innsending) {
            is NyOppgaveversjon -> {
                val skalOppdatere = nyEksternversjon(innsending.dto, tx)

                if (skalOppdatere) {
                    return lagreNyOppgaveversjon(innsending.dto, tx)
                } else {
                    return null
                }
            }
            is VaskOppgaveversjon -> {
                val eksisterer = oppgaveV3Repository.hentOppgaveIdStatusOgHøyesteInternversjon(tx, innsending.dto.eksternId, innsending.dto.type, innsending.dto.område).first != null
                if (eksisterer) {
                    return vaskEksisterendeOppgaveversjon(innsending.dto, innsending.eventNummer, tx)
                } else {
                    return lagreNyOppgaveversjon(innsending.dto, tx)
                }
            }
        }
    }

    private fun lagreNyOppgaveversjon(oppgaveDto: OppgaveDto, tx: TransactionalSession): OppgaveV3 {
        val område = områdeRepository.hentOmråde(oppgaveDto.område, tx)
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(
            område = område.eksternId,
            eksternId = oppgaveDto.type,
            tx = tx
        )

        val aktivOppgaveVersjon = oppgaveV3Repository.hentAktivOppgave(oppgaveDto.eksternId, oppgavetype, tx)
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

    @VisibleForTesting
    fun hentAktivOppgave(eksternId: String, oppgavetypeEksternId: String, områdeEksternId: String, tx: TransactionalSession) : OppgaveV3 {
        tx.run {
            val område = områdeRepository.hentOmråde(områdeEksternId, tx)
            val oppgavetype = oppgavetypeRepository.hentOppgavetype(område, oppgavetypeEksternId, tx)
            return oppgaveV3Repository.hentAktivOppgave(eksternId, oppgavetype, tx)!!
        }
    }

    fun hentOppgaveversjon(
        område: String,
        oppgavetype: String,
        eksternId: String,
        eksternVersjon: String,
        tx: TransactionalSession
    ): OppgaveV3 {
        return oppgaveV3Repository.hentOppgaveversjon(
            område = områdeRepository.hentOmråde(område, tx),
            oppgavetype = oppgavetypeRepository.hentOppgavetype(område, oppgavetype, tx),
            eksternId = eksternId,
            eksternVersjon = eksternVersjon,
            tx = tx
        )
    }

    fun hentOppgaveversjon(
        område: String,
        oppgavetype: String,
        eksternId: String,
        internVersjon: Int,
        tx: TransactionalSession
    ) : OppgaveV3? {
        val område = områdeRepository.hentOmråde(område, tx)
        return oppgaveV3Repository.hentOppgaveversjon(
            område = område,
            oppgavetype = oppgavetypeRepository.hentOppgavetype(område, oppgavetype, tx),
            eksternId = eksternId,
            internVersjon = internVersjon,
            tx
        )
    }

    fun slettAktivOppgave(innkommendeOppgave: OppgaveV3, tx: TransactionalSession) {
        AktivOppgaveRepository.slettAktivOppgave(tx, innkommendeOppgave)
    }

    fun slettOppgave(oppgavenøkkel: OppgaveNøkkelDto, tx: TransactionalSession) {
        oppgaveV3Repository.slettOppgave(oppgavenøkkel, tx)
    }

    fun vaskEksisterendeOppgaveversjon(oppgaveDto: OppgaveDto, eventNr: Int, tx: TransactionalSession) : OppgaveV3 {
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(
            område = oppgaveDto.område,
            eksternId = oppgaveDto.type,
            tx = tx
        )

        val område = områdeRepository.hentOmråde(oppgaveDto.område, tx)

        val forrigeOppgaveversjon = if (eventNr > 0) {
            oppgaveV3Repository.hentOppgaveversjon(område, oppgavetype, oppgaveDto.eksternId, eventNr, tx)
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

        //historikkvasktjenesten skal sørge for at oppgaven med internVersjon = eventNr faktisk eksisterer
        oppgaveV3Repository.slettFeltverdier(
            eksternId = innkommendeOppgave.eksternId,
            internVersjon = eventNr,
            tx = tx
        )

        oppgaveV3Repository.lagreFeltverdierForDatavask(
            oppgave = innkommendeOppgave,
            internVersjon = eventNr,
            tx = tx
        )

        oppgaveV3Repository.oppdaterReservasjonsnøkkelStatusOgEksternVersjon(
            eksternId = innkommendeOppgave.eksternId,
            eksternVersjon = innkommendeOppgave.eksternVersjon,
            status = innkommendeOppgave.status,
            internVersjon = eventNr,
            reservasjonsnokkel = innkommendeOppgave.reservasjonsnøkkel,
            tx = tx)

        return oppgaveV3Repository.hentOppgaveversjon(
            område = område,
            oppgavetype = oppgavetype,
            eksternId = oppgaveDto.eksternId,
            internVersjon = eventNr,
            tx = tx
        )!!
    }

    fun hentHøyesteInternVersjon(oppgaveEksternId: String, opppgaveTypeEksternId: String, områdeEksternId: String, tx: TransactionalSession): Int? {
        val (_, _, versjon) = oppgaveV3Repository.hentOppgaveIdStatusOgHøyesteInternversjon(tx, oppgaveEksternId, opppgaveTypeEksternId, områdeEksternId)
        return versjon
    }

    fun hentSisteEksternVersjon( områdeEksternId: String, oppgaveTypeEksternId: String, oppgaveEksternId: String, tx: TransactionalSession): String? {
        val område = områdeRepository.hentOmråde(områdeEksternId, tx)
        val oppgavetype = oppgavetypeRepository.hentOppgavetype(område, oppgaveTypeEksternId, tx)
        return oppgaveV3Repository.hentAktivOppgaveEksternversjon(område, oppgavetype, oppgaveEksternId, tx = tx)
    }

    fun nyEksternversjon(oppgaveDto: OppgaveDto, tx: TransactionalSession): Boolean {
        return !oppgaveV3Repository.finnesFraFør(tx, oppgaveDto.eksternId, oppgaveDto.eksternVersjon)
    }

    fun tellAntall(): Pair<Long, Long> {
        return oppgaveV3Repository.tellAntall()
    }
}