package no.nav.k9.fagsystem.k9sak

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.domene.modell.Fagsystem
import org.slf4j.LoggerFactory
import java.util.*

class OppgaveTjenesteSak(
    oppgaveRepository: OppgaveRepositoryV2,
    val k9SakRepository: K9SakRepository,
) : OppgaveTjenesteV2(oppgaveRepository) {

    private val log = LoggerFactory.getLogger(OppgaveTjenesteSak::class.java)

    override fun hentBehandling(referanse: UUID): FagsystemBehandling? {
        return oppgaveRepository.hentBehandling(referanse.toString())?.run {
            log.info("Henter behandling fra db $eksternReferanse")
            val k9sak = k9SakRepository.hentFagsystemData(referanse)
            FagsystemBehandling(this, k9sak)
        }
    }

    fun hentAlleAktiveOppgaverForFagsystem(): List<FagsystemBehandling> {
        val aktiveBehandlinger = oppgaveRepository.hentAlleAktiveOppgaverForFagsystemGruppertPrReferanse(Fagsystem.K9SAK)
        val behandlingK9Sak = k9SakRepository.hentSaksbehandlingForAlle(aktiveBehandlinger.keys)
        return aktiveBehandlinger.map { (k, v) -> FagsystemBehandling(v, behandlingK9Sak[k]) }
    }

    override fun opprettNyBehandling(oppgave: Oppgave): FagsystemBehandling {
        log.info("Oppretter ny fagsystemBehandling ${oppgave.eksternId}")

        return FagsystemBehandling(
            behandling = super.opprettNyBehandling(oppgave = oppgave),
            fagsystemBehandlingdata = FagsystemBehandlingData.opprettFra(oppgave.eksternId, oppgave)
        )
    }
}