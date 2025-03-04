package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.utils.Cache

class FerdigstiltEnhet(val saksbehandlerRepository: SaksbehandlerRepository) : Feltutleder {
    // For å speede opp henting av enhet for saksbehandler ved historikkvask, kan nok fjernes etter at feltet er migrert
    private val saksbehandlerCache = Cache<String, String?>(null)

    override fun utled(innkommendeOppgave: OppgaveV3, aktivOppgaveVersjon: OppgaveV3?): OppgaveFeltverdi? {
        if (innkommendeOppgave.status != Oppgavestatus.LUKKET) return null
        val saksbehandler = innkommendeOppgave.hentVerdi("ansvarligSaksbehandler")
            ?: innkommendeOppgave.hentVerdi("ansvarligSaksbehandlerForToTrinn")
        return OppgaveFeltverdi(
            oppgavefelt = innkommendeOppgave.hentFelt("ferdigstiltEnhet"),
            verdi = aktivOppgaveVersjon?.hentVerdi("ferdigstiltEnhet")
                ?: hentEnhetFraCache(saksbehandler)
                ?: return null,
            verdiBigInt = null
        )
    }

    private fun hentEnhetFraCache(saksbehandler: String?) =
        saksbehandler?.let {
            saksbehandlerCache.hent(it) {
                saksbehandlerRepository.finnSaksbehandlerMedIdentEkskluderKode6(it)?.enhet
            }
        }
}