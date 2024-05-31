package no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgave

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.datainnlasting.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste

class OppgaveTjenesteKorreksjoner(
    private val oppgaveV3Repository: OppgaveV3Repository,
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val områdeRepository: OmrådeRepository,
    private val reservasjonTjeneste: ReservasjonV3Tjeneste
) {
    fun oppdaterEksisterendeOppgaveversjon(oppgaveDto: OppgaveDto, eventNr: Long, høyesteInternVersjon: Long, tx: TransactionalSession) {
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

        oppgaveV3Repository.lagreFeltverdierForDatavask(
            eksternId = innkommendeOppgave.eksternId,
            internVersjon = eventNr,
            oppgaveFeltverdier = innkommendeOppgave.felter,
            aktiv = eventNr == høyesteInternVersjon,
            oppgavestatus = Oppgavestatus.fraKode(oppgaveDto.status),
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
}