package no.nav.k9.los.db

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.Aksjonspunkter
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.domene.repository.OppgaveRepository
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class RekkefølgeJsonBArrayTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `Endrer rekkefølgen i arrayene seg?`() {

        val oppgaveRepository = get<OppgaveRepository>()

        val eksternId = UUID.randomUUID()
        IntRange(1, 10).forEach {
            lagreOppgave(eksternId, oppgaveRepository, it)
            assert(oppgaveRepository.hent(eksternId).behandlendeEnhet == "Enhet$it")
        }

    }

    private fun lagreOppgave(
        eksternId: UUID,
        oppgaveRepository: OppgaveRepository,
        i: Int
    ) {
        val oppgave1 = Oppgave(
            
            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet$i",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now().minusDays(23),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.FORSTEGANGSSOKNAD,
            fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            aktiv = true,
            system = "K9SAK",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = eksternId,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
    }
}

