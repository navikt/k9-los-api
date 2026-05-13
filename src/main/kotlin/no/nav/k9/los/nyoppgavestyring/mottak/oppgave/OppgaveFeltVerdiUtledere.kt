package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9FeltIder
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import java.time.Duration
import java.time.LocalDateTime

class OppgaveFeltVerdiUtledere(private val saksbehandlerRepository: SaksbehandlerRepository) {
    // Cacher saksbehandlers enhet fordi historikkvask vil gjøre veldig mange kall
    private val saksbehandlerCache = Cache<String, String?>(null)

    fun utledFerdigstiltTidspunkt(
        oppgavestatus: Oppgavestatus,
        forrigeOppgave: OppgaveV3?,
        eventTid: LocalDateTime?
    ): OppgaveFeltverdiDto =
        OppgaveFeltverdiDto(
            nøkkel = K9FeltIder.FERDIGSTILT_TIDSPUNKT,
            verdi = if (oppgavestatus == Oppgavestatus.LUKKET) forrigeOppgave?.hentVerdi(K9FeltIder.FERDIGSTILT_TIDSPUNKT)
                ?: eventTid?.toString() else null
        )

    fun utledFerdigstiltEnhet(
        oppgavestatus: Oppgavestatus,
        forrigeOppgave: OppgaveV3?,
        ansvarligSaksbehandler: String?
    ): OppgaveFeltverdiDto =
        OppgaveFeltverdiDto(
            nøkkel = K9FeltIder.FERDIGSTILT_ENHET,
            verdi = if (oppgavestatus == Oppgavestatus.LUKKET) forrigeOppgave?.hentVerdi(K9FeltIder.FERDIGSTILT_ENHET)
                ?: ansvarligSaksbehandler?.let { finnSaksbehandlersEnhet(it) } else null
        )

    private fun finnSaksbehandlersEnhet(saksbehandlerIdent: String) = saksbehandlerCache.hent(saksbehandlerIdent, Duration.ofHours(3)) {
        saksbehandlerRepository.finnSaksbehandlerMedIdentEkskluderKode6(saksbehandlerIdent)?.enhet
    }
}