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

    override fun utled(innkommendeOppgave: OppgaveV3, aktivOppgaveVersjon: OppgaveV3, tx: TransactionalSession): OppgaveV3 {

        val akkumulertFraTidligere = aktivOppgaveVersjon.hentVerdi(AKKUMULERT_VENTETID_SAKSBEHANDLER)?.let { Duration.parse(it) } ?: Duration.ZERO
        val akkumulertVentetidSaksbehandler = Duration.between(aktivOppgaveVersjon.endretTidspunkt, innkommendeOppgave.endretTidspunkt).plus(akkumulertFraTidligere)

        val felter = mutableListOf<OppgaveFeltverdi>() //TODO: Hvorfor filterNot? Må vi filtrere bort en forekomst med en utleder, men ikke verdi?
        felter.addAll(innkommendeOppgave.felter.filterNot { // TODO: Burde ikke få treff?
            it.oppgavefelt.feltDefinisjon.eksternId == AKKUMULERT_VENTETID_SAKSBEHANDLER
        })
        felter.add(
            OppgaveFeltverdi(
                oppgavefelt = innkommendeOppgave.felter.first {
                    it.oppgavefelt.feltDefinisjon.eksternId == AKKUMULERT_VENTETID_SAKSBEHANDLER
                }.oppgavefelt,
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
    }
}