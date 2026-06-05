package no.nav.k9.los.oppgavemottak.feltutlederforlagring

import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdi
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.infrastruktur.utils.Cache

class FerdigstiltEnhet(val saksbehandlerRepository: SaksbehandlerRepository) : Feltutleder {
    // For å speede opp henting av enhet for saksbehandler ved historikkvask, kan nok fjernes etter at feltet er migrert
    private val saksbehandlerCache = Cache<String, String?>(null)

    override fun utled(innkommendeOppgave: OppgaveV3, aktivOppgaveVersjon: OppgaveV3?): OppgaveFeltverdi? {
        if (innkommendeOppgave.status != Oppgavestatus.LUKKET) return null
        val saksbehandler = innkommendeOppgave.hentVerdi("ansvarligSaksbehandler")
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
                val saksbehandlerEnhet = saksbehandlerRepository.finnSaksbehandlerMedIdentEkskluderKode6(it)?.enhet?.uppercase()
                saksbehandlerEnhet?.let { Regex("^\\d{4}").find(it)?.value }
            }
        }
}