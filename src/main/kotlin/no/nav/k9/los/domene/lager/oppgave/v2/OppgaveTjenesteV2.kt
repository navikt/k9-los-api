package no.nav.k9.los.domene.lager.oppgave.v2

import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

open class OppgaveTjenesteV2(
    val oppgaveRepository: OppgaveRepositoryV2,
    val migreringstjeneste: BehandlingsmigreringTjeneste,
    val tm: TransactionalManager
) {
    private val log = LoggerFactory.getLogger(OppgaveTjenesteV2::class.java)

    fun nyOppgaveHendelse(eksternId: String, hendelser: OppgaveHendelse) {
        nyeOppgaveHendelser(eksternId, listOf(hendelser))
    }

    fun nyeOppgaveHendelser(eksternId: String, hendelser: List<OppgaveHendelse>) {
        log.info("Starter prosessering av hendelser ${hendelser.map { it::class.simpleName }}")
        tm.transaction { tx ->
            val behandling = oppgaveRepository.hentBehandling(eksternId, tx)
                ?: hendelser.hentBehandlingEndretEvent()?.tilBehandling()
                ?: migreringstjeneste.hentBehandlingFraTidligereProsessEvents(eksternId)
                ?: throw IllegalStateException("Mottatt hendelse uten å ha behandling. $eksternId")

            if (!behandling.erFerdigstilt()) {
                hendelser.forEach { behandling.nyHendelse(it) }
                oppgaveRepository.lagre(behandling, tx)
            } else {
                log.warn("Mottok hendelse for allerede ferdigstilt behandling $eksternId")
            }
        }
    }

    fun List<OppgaveHendelse>.hentBehandlingEndretEvent(): BehandlingEndret? {
        val behandlingEndretEvent = firstOrNull { it is BehandlingEndret }
        return (behandlingEndretEvent as BehandlingEndret?)
    }

    fun avsluttBehandling(eksternId: String, ferdigstillelse: Ferdigstillelse) {
        oppgaveRepository.hentBehandling(eksternId)
            ?.also { behandling -> behandling.ferdigstill(ferdigstillelse) }
            ?.run { oppgaveRepository.lagre(this) }
    }

}

interface OppgaveHendelse {
    val tidspunkt: LocalDateTime
}

data class BehandlingEndret(
    override val tidspunkt: LocalDateTime,
    val eksternReferanse: String,
    val fagsystem: Fagsystem,
    val ytelseType: FagsakYtelseType,
    val behandlingType: String?,
    val søkersId: Ident?
) : OppgaveHendelse {

    fun tilBehandling(): Behandling {
        return Behandling.ny(
            opprettet = tidspunkt,
            eksternReferanse = eksternReferanse,
            fagsystem = fagsystem,
            ytelseType = ytelseType,
            behandlingType = behandlingType,
            søkersId = søkersId
        )
    }
}

data class OpprettOppgave(
    override val tidspunkt: LocalDateTime,
    val oppgaveKode: String,
    val frist: LocalDateTime?
): OppgaveHendelse

data class FerdigstillOppgave(
    override val tidspunkt: LocalDateTime,
    override val ansvarligSaksbehandlerIdent: String? = null,
    override val behandlendeEnhet: String? = null,
    val oppgaveKode: String?
) : Ferdigstillelse, OppgaveHendelse

data class AvbrytOppgave(
    override val tidspunkt: LocalDateTime,
    val oppgaveKode: String?
) : OppgaveHendelse

data class FerdigstillBehandling(
    override val tidspunkt: LocalDateTime,
    override val ansvarligSaksbehandlerIdent: String? = null,
    override val behandlendeEnhet: String? = null,
) : Ferdigstillelse, OppgaveHendelse
