package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import java.time.Duration
import java.time.temporal.ChronoUnit

abstract class AkkumulerDuration(
    val beslutningsfelt: String,
    val målfelt: String
) : Feltutleder {

    override fun utled(
        innkommendeOppgave: OppgaveV3,
        aktivOppgaveVersjon: OppgaveV3?
    ): OppgaveFeltverdi? {

        if (aktivOppgaveVersjon == null) {
            return null
        }

        val akkumulertFraTidligere =
            aktivOppgaveVersjon.hentVerdi(målfelt)?.let { Duration.parse(it) }
                ?: Duration.ZERO
        val akkumulertVentetidSaksbehandler =
            if (aktivOppgaveVersjon.hentVerdi(beslutningsfelt).toBoolean()) {
                Duration.between(aktivOppgaveVersjon.endretTidspunkt, innkommendeOppgave.endretTidspunkt)
                    .plus(akkumulertFraTidligere)
            } else {
                Duration.ZERO.plus(akkumulertFraTidligere)
            }
        val avrundet = akkumulertVentetidSaksbehandler.truncatedTo(ChronoUnit.SECONDS)

        return OppgaveFeltverdi(
            oppgavefelt = innkommendeOppgave.hentFelt(målfelt),
            verdi = avrundet.toString(),
            verdiBigInt = null
        )
    }
}