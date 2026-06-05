package no.nav.k9.los.oppgavemottak.feltutlederforlagring

import no.nav.k9.los.oppgavemottak.OppgaveFeltverdi
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus

object FerdigstiltTidspunkt : Feltutleder {
    override fun utled(innkommendeOppgave: OppgaveV3, aktivOppgaveVersjon: OppgaveV3?): OppgaveFeltverdi? {
        return if (innkommendeOppgave.status == Oppgavestatus.LUKKET) {
            OppgaveFeltverdi(
                oppgavefelt = innkommendeOppgave.hentFelt("ferdigstiltTidspunkt"),
                verdi = aktivOppgaveVersjon?.hentVerdi("ferdigstiltTidspunkt")
                    ?: innkommendeOppgave.endretTidspunkt.toString(),
                verdiBigInt = null
            )
        } else null
    }
}