package no.nav.k9.los.nyoppgavestyring.feltutledere

import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import java.time.Duration

class AkkumulertVentetidSaksbehandler : Feltutleder {

    private val AKKUMULERT_VENTETID_SAKSBEHANDLER = "akkumulertVentetidSaksbehandler"
    private val AVVENTER_SAKSBEHANDLER = "avventerSaksbehandler"

    override fun utled(
        innkommendeOppgave: OppgaveV3,
        aktivOppgaveVersjon: OppgaveV3?
    ): OppgaveFeltverdi? {

        if (aktivOppgaveVersjon == null) {
            return null
        }

        val akkumulertFraTidligere =
            aktivOppgaveVersjon.hentVerdi(AKKUMULERT_VENTETID_SAKSBEHANDLER)?.let { Duration.parse(it) }
                ?: Duration.ZERO
        val akkumulertVentetidSaksbehandler =
            if (aktivOppgaveVersjon.hentVerdi(AVVENTER_SAKSBEHANDLER).toBoolean()) {
                Duration.between(aktivOppgaveVersjon.endretTidspunkt, innkommendeOppgave.endretTidspunkt)
                    .plus(akkumulertFraTidligere)
            } else {
                Duration.ZERO.plus(akkumulertFraTidligere)
            }

        return OppgaveFeltverdi(
                oppgavefelt = innkommendeOppgave.hentFelt(AKKUMULERT_VENTETID_SAKSBEHANDLER),
                verdi = akkumulertVentetidSaksbehandler.toString()
            )
    }
}