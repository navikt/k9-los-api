package no.nav.k9.los.oppgavemottak

import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
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
            nøkkel = "ferdigstiltTidspunkt",
            verdi = if (oppgavestatus == Oppgavestatus.LUKKET) forrigeOppgave?.hentVerdi("ferdigstiltTidspunkt")
                ?: eventTid?.toString() else null
        )

    fun utledFerdigstiltEnhet(
        oppgavestatus: Oppgavestatus,
        forrigeOppgave: OppgaveV3?,
        ansvarligSaksbehandler: String?
    ): OppgaveFeltverdiDto =
        OppgaveFeltverdiDto(
            nøkkel = "ferdigstiltEnhet",
            verdi = if (oppgavestatus == Oppgavestatus.LUKKET) forrigeOppgave?.hentVerdi("ferdigstiltEnhet")
                ?: ansvarligSaksbehandler?.let { finnSaksbehandlersEnhet(it) } else null
        )

    private fun finnSaksbehandlersEnhet(saksbehandlerIdent: String) = saksbehandlerCache.hent(saksbehandlerIdent, Duration.ofHours(3)) {
        saksbehandlerRepository.finnSaksbehandlerMedIdentEkskluderKode6(saksbehandlerIdent)?.enhet
    }
}