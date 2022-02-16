package no.nav.k9.fagsystem.k9sak

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.domene.modell.Fagsystem
import org.slf4j.LoggerFactory
import java.util.*

class OppgaveTjenesteK9Sak(
    oppgaveRepository: OppgaveRepositoryV2,
    val k9SakRepository: K9SakRepository,
) : OppgaveTjenesteV2(oppgaveRepository) {

    private val log = LoggerFactory.getLogger(OppgaveTjenesteK9Sak::class.java)

    override fun hentBehandling(referanse: UUID): BehandlingK9Sak? {
        return oppgaveRepository.hentBehandling(referanse.toString())?.run {
            log.info("Henter behandling fra db $eksternReferanse")
            val k9sak = k9SakRepository.hentFagsystemData(referanse)
            BehandlingK9Sak(this, k9sak)
        }
    }

    fun hentAlleAktiveOppgaverForFagsystem(): List<BehandlingK9Sak> {
        val aktiveBehandlinger = oppgaveRepository.hentAlleAktiveOppgaverForFagsystemGruppertPrReferanse(Fagsystem.K9SAK)
        val behandlingK9Sak = k9SakRepository.hentSaksbehandlingForAlle(aktiveBehandlinger.keys)
        return aktiveBehandlinger.map { (k, v) -> BehandlingK9Sak(v, behandlingK9Sak[k]) }
    }

    override fun opprettNyBehandling(oppgave: Oppgave): BehandlingK9Sak {
        log.info("Oppretter ny fagsystemBehandling ${oppgave.eksternId}")

        return BehandlingK9Sak(
            behandling = super.opprettNyBehandling(oppgave = oppgave),
            fagsystemBehandlingdata = BehandlingdataK9Sak.opprettFra(oppgave.eksternId, oppgave)
        )
    }
}