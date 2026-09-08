package no.nav.k9.los.domeneadaptere.ung.akt.eventtiloppgave.sak

import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.ung.akt.kodeverk.AktivitetspengerOppgavetypenavn
import no.nav.k9.los.oppgavemottak.NyOppgaveVersjonInnsending
import no.nav.k9.los.oppgavemottak.NyOppgaveversjon
import no.nav.k9.los.oppgavemottak.OppgaveDto
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import no.nav.k9.los.oppgavemottak.OppgaveV3

class SakEventTilOppgaveMapperDel2() {
    fun lagOppgaveDto(eventLagret: EventLagret.UngSak, forrigeOppgave: OppgaveV3, eventnummer: Int): NyOppgaveVersjonInnsending {
        if (eventLagret.fagsystem != Fagsystem.UNGSAK) {
            throw IllegalArgumentException("Kan kun mappe UNGSAK event til oppgave")
        }
        val oppgaveDto = OppgaveDto(
            eksternId = eventLagret.eksternId,
            eksternVersjon = eventLagret.eksternVersjon,
            type = AktivitetspengerOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1,
            status = forrigeOppgave.status.name,
            endretTidspunkt = eventLagret.opprettet,
            reservasjonsnøkkel = forrigeOppgave.reservasjonsnøkkel,
            feltverdier = forrigeOppgave.felter.map {
                OppgaveFeltverdiDto(
                    nøkkel = it.oppgavefelt.feltDefinisjon.eksternId,
                    verdi = it.verdi,
                )
            },
        )
        return NyOppgaveversjon(oppgaveDto)
    }
}