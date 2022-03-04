package no.nav.k9.domene.lager.oppgave.v2

import org.slf4j.LoggerFactory
import java.time.LocalDateTime

open class OppgaveTjenesteV2(
    val oppgaveRepository: OppgaveRepositoryV2,
) {
    private val log = LoggerFactory.getLogger(OppgaveTjenesteV2::class.java)

    fun nyOppgaveHendelse(eksternId: String, hendelse: OppgaveHendelse, opprettFunc: () -> Behandling? = { null } ) {
        val behandling = hentEllerOpprettFra(eksternId, opprettFunc)

        when(hendelse) {
                is OpprettOppgave -> behandling.nyOppgave(hendelse)
                is Ferdigstillelse -> behandling.lukkAktiveOppgaver(hendelse)
            }
        oppgaveRepository.lagre(behandling)
    }

    fun opprettBehandling(eksternId: String, opprettFunc: () -> Behandling? = { null }) {
        hentEllerOpprettFra(eksternId, opprettFunc).run { oppgaveRepository.lagre(this) }
    }

    fun avsluttBehandling(eksternId: String, ferdigstillelse: Ferdigstillelse) {
        oppgaveRepository.hentBehandling(eksternId)
            ?.also { behandling -> behandling.ferdigstill(ferdigstillelse) }
            ?.run { oppgaveRepository.lagre(this) }
    }

    fun hentEllerOpprettFra(eksternId: String, opprettFunc: () -> Behandling? = { null }): Behandling {
        return oppgaveRepository.hentBehandling(eksternId)?.also { log.info("Bruker eksisterende behandling for $eksternId") }
            ?: opprettFunc()?.also { log.info("Oppretter ny behandling for $eksternId") }
        ?: throw IllegalStateException("Finner ikke behandling og kan ikke opprette ny for $eksternId")
    }
}

interface OppgaveHendelse {
    val tidspunkt: LocalDateTime
}

data class Ferdigstillelse(
    override val tidspunkt: LocalDateTime,
    val ansvarligSaksbehandlerIdent: String? = null,
    val behandlendeEnhet: String? = null
) : OppgaveHendelse

data class OpprettOppgave(
    override val tidspunkt: LocalDateTime,
    val oppgaveKode: String,
    val frist: LocalDateTime?
): OppgaveHendelse