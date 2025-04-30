package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper

// Fjernes når V1 skal vekk
class ReservasjonOversetter(
    private val oppgaveV3RepositoryMedTxWrapper: OppgaveRepositoryTxWrapper,
) {
    fun hentReservasjonsnøkkelForOppgavenøkkel(
        oppgaveNøkkel: OppgaveNøkkelDto
    ): String {
        return oppgaveV3RepositoryMedTxWrapper.hentOppgave(
                oppgaveNøkkel.områdeEksternId,
                oppgaveNøkkel.oppgaveEksternId
            ).reservasjonsnøkkel
    }
}