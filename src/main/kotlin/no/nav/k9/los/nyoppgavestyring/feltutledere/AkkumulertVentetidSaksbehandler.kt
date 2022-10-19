package no.nav.k9.los.nyoppgavestyring.feltutledere

import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import java.time.Duration

class AkkumulertVentetidSaksbehandler : Feltutleder {

    private val AKKUMULERT_VENTETID_SAKSBEHANDLER = "akkumulertVentetidSaksbehandler"
    private val AVVENTER_SAKSBEHANDLER = "avventerSaksbehandler"

    override val påkrevdeFelter = hashMapOf(
        AKKUMULERT_VENTETID_SAKSBEHANDLER to "Duration",
        AVVENTER_SAKSBEHANDLER to "boolean"
    )

    override fun utled(
        innkommendeOppgave: OppgaveV3,
        aktivOppgaveVersjon: OppgaveV3?,
        tx: TransactionalSession
    ): OppgaveV3 {

        aktivOppgaveVersjon?.let {
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


            val felter = innkommendeOppgave.felter.toMutableList()

            felter.add(
                OppgaveFeltverdi(
                    oppgavefelt = innkommendeOppgave.oppgavetype.oppgavefelter.first { oppgavefelt ->
                        oppgavefelt.feltDefinisjon.eksternId == AKKUMULERT_VENTETID_SAKSBEHANDLER},
                    verdi = akkumulertVentetidSaksbehandler.toString()
                )
            )

            return OppgaveV3(
                eksternId = innkommendeOppgave.eksternId,
                eksternVersjon = innkommendeOppgave.eksternVersjon,
                oppgavetype = innkommendeOppgave.oppgavetype,
                status = innkommendeOppgave.status,
                endretTidspunkt = innkommendeOppgave.endretTidspunkt,
                kildeområde = innkommendeOppgave.kildeområde,
                felter = felter
            )
        } ?: return innkommendeOppgave
    }
}