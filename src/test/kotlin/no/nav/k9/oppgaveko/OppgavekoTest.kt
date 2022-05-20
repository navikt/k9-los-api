package no.nav.k9.oppgaveko

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.AbstractPostgresTest
import no.nav.k9.Configuration
import no.nav.k9.KoinProfile
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.AndreKriterierType
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.Enhet
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.KøSortering
import no.nav.k9.domene.modell.OppgaveKø
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.AndreKriterierDto
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class OppgavekoTest :KoinTest, AbstractPostgresTest() {

    @JvmField
    @RegisterExtension
    val koinTestRule = KoinTestExtension.create {
        modules(buildAndTestConfig(dataSource))
    }

    @Test
    fun `Oppgavene tilfredsstiller filtreringskriteriene i køen`() = runBlocking {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()

        val config = mockk<Configuration>()
        val oppgaveTjeneste = get<OppgaveTjeneste>()

        val uuid = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = uuid,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD),
            filtreringYtelseTyper = mutableListOf(FagsakYtelseType.PLEIEPENGER_SYKT_BARN),
            filtreringAndreKriterierType = mutableListOf(
                AndreKriterierDto(
                    uuid.toString(),
                    AndreKriterierType.TIL_BESLUTTER,
                    true,
                    true
                ),
                AndreKriterierDto(
                    uuid.toString(),
                    AndreKriterierType.AVKLAR_MEDLEMSKAP,
                    true,
                    false
                )
            ),
            enhet = Enhet.NASJONAL,
            fomDato = LocalDate.now().minusDays(100),
            tomDato = LocalDate.now().plusDays(100),
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(uuid) { oppgaveko }

        val oppgave1 = Oppgave(
            
            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = true,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
        val oppgave2 = Oppgave(
            behandlingId = 78567,
            fagsakSaksnummer = "5Yagdt",
            aktorId = "675864",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
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
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap(), emptyList()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = true,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = true, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )

        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }

        oppgaveko.leggOppgaveTilEllerFjernFraKø(oppgave1, reservasjonRepository)
        oppgaveko.leggOppgaveTilEllerFjernFraKø(oppgave2, reservasjonRepository)
        oppgaveKøRepository.lagre(oppgaveko.id) {
             oppgaveko
        }
        every { KoinProfile.LOCAL == config.koinProfile() } returns true
        val hent = oppgaveTjeneste.hentOppgaver(oppgaveko.id)
        assert(hent.size == 1)
        assert(hent[0].avklarArbeidsforhold)
        assert(!hent[0].selvstendigFrilans)
    }
}

