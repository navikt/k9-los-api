package no.nav.k9.domene.lager.oppgave.v2

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.Fagsystem
import org.slf4j.LoggerFactory
import java.util.*

abstract class OppgaveTjenesteV2(
    val oppgaveRepository: OppgaveRepositoryV2,
) {
    private val log = LoggerFactory.getLogger(OppgaveTjenesteV2::class.java)

    open fun nyOppgavehendelse(oppgave: Oppgave) {
        val behandling = hentEllerOpprettFra(oppgave)

        log.info("Oppdaterer behandling ${oppgave.eksternId} med nytt prosessevent")
        val eventResultat = oppgave.aksjonspunkter.eventResultat()

        val manuelleAktiveAksjonspunkter = oppgave.aksjonspunkter.manuelleAksjonspunkter()
        manuelleAktiveAksjonspunkter.forEach { manueltAksjonspunkt ->
            behandling.nyOppgave(oppgave.eventTid, manueltAksjonspunkt.key.kode)
        }

        if (eventResultat.setterOppgavePåVent()) {
            behandling.settPåVent()
        } else if (eventResultat.lukkerOppgave()) {
            behandling.lukkAktiveOppgaver(
                oppgave.eventTid,
                ansvarligSaksbehandler = oppgave.ansvarligSaksbehandlerIdent,
                enhet = oppgave.behandlendeEnhet
            )
        }
        if (oppgave.behandlingStatus == BehandlingStatus.AVSLUTTET) {
            behandling.lukkAktiveOppgaver(
                oppgave.eventTid,
                ansvarligSaksbehandler = oppgave.ansvarligSaksbehandlerIdent,
                enhet = oppgave.behandlendeEnhet
            )
        }
        oppgaveRepository.lagre(behandling)
    }

    open fun hentEllerOpprettFra(oppgave: Oppgave): Behandling {
        return hentBehandling(oppgave.eksternId) ?: opprettNyBehandling(oppgave)
    }

    open fun hentBehandling(referanse: UUID): Behandling? {
        return oppgaveRepository.hentBehandling(referanse.toString())
    }

    open fun opprettNyBehandling(oppgave: Oppgave): Behandling {
        log.info("Oppretter ny behandling $oppgave.eksternId")
        return Behandling(
            id = UUID.randomUUID(),
            eksternReferanse = oppgave.eksternId.toString(),
            fagsystem = Fagsystem.fraKode(oppgave.system),
            ytelseType = oppgave.fagsakYtelseType,
            søkersId = Ident(oppgave.aktorId, Ident.IdType.AKTØRID),
            kode6 = oppgave.kode6,
            skjermet = oppgave.skjermet
        )
    }
}