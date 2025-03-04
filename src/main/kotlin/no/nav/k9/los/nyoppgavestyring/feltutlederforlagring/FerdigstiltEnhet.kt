package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus

class FerdigstiltEnhet(val saksbehandlerRepository: SaksbehandlerRepository) : Feltutleder {
    override fun utled(innkommendeOppgave: OppgaveV3, aktivOppgaveVersjon: OppgaveV3?): OppgaveFeltverdi? {
        if (innkommendeOppgave.status != Oppgavestatus.LUKKET) return null
        val saksbehandler = innkommendeOppgave.hentVerdi("ansvarligSaksbehandler")
            ?: innkommendeOppgave.hentVerdi("ansvarligSaksbehandlerForToTrinn")
        return OppgaveFeltverdi(
            oppgavefelt = innkommendeOppgave.hentFelt("ferdigstiltEnhet"),
            verdi = aktivOppgaveVersjon?.hentVerdi("ferdigstiltEnhet")
                ?: saksbehandler?.let { saksbehandlerRepository.finnSaksbehandlerMedIdentEkskluderKode6(it) }?.enhet
                ?: return null,
            verdiBigInt = null
        )
    }
}