package no.nav.k9.los.domene.repository

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.Aksjonspunkter
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.los.nyoppgavestyring.reservasjon.BehandletOppgave
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertSame

class StatistikkRepositoryTest : AbstractK9LosIntegrationTest() {

    @Test
    fun skalFylleMedTommeElementerDersomViIkkeHarDataPåDenDagen() {

        val statistikkRepository  = get<StatistikkRepository>()

        val hentFerdigstilte = statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker()

        val omsorgspenger = hentFerdigstilte.take(FagsakYtelseType.values().size*5).filter { it.fagsakYtelseType == FagsakYtelseType.OMSORGSPENGER }
        assertSame(5, omsorgspenger.size)
        stopKoin()
    }

    @Test
    fun skalFylleMedTommeElementerDersomVdiIkkeHarDataPåDenDagenIdempotent() {

        val statistikkRepository  = get<StatistikkRepository>()

        val oppgave = Oppgave(
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
            fagsakYtelseType = FagsakYtelseType.OMSORGSPENGER,
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
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType, oppgave.eventTid.toLocalDate().minusDays(1))){
            it.nye.add(oppgave.eksternId.toString())
            it
        }
        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType, oppgave.eventTid.toLocalDate().minusDays(1))){
            it.nye.add(oppgave.eksternId.toString())
            it
        }
        val hentFerdigstilte = statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker()
        val omsorgspenger = hentFerdigstilte.reversed().filter { it.fagsakYtelseType == FagsakYtelseType.OMSORGSPENGER }
        assertSame(1, omsorgspenger.find { it.behandlingType == BehandlingType.FORSTEGANGSSOKNAD }?.nye?.size )

    }

    @Test
    fun skalFiltrereUnikeSistBehandledeSaker() {

        val statistikkRepository  = get<StatistikkRepository>()

        val oppgave = BehandletOppgave(
            behandlingId = null,
            journalpostId = null,
            system = "K9SAK",
            navn = "Trøtt Bolle",
            eksternId = UUID.randomUUID(),
            personnummer = "84757594394",
            saksnummer = "PLUy6"
        )
        val oppgave2 = BehandletOppgave(
            behandlingId = null,
            journalpostId = null,
            system = "K9SAK",
            navn = "Walter White",
            eksternId = UUID.randomUUID(),
            personnummer = "84757594394",
            saksnummer = "PLUy6"
        )
        val oppgave3 = BehandletOppgave(
            behandlingId = 78567,
            journalpostId = null,
            system = "K9SAK",
            navn = "Dorull Talentfull",
            eksternId = UUID.randomUUID(),
            personnummer = "84757594394",
            saksnummer = "Z34Yt"
        )
        val oppgave4 = BehandletOppgave(
            behandlingId = null,
            journalpostId = "465789506",
            system = "PUNSJ",
            navn = "Knott Klumpete",
            eksternId = UUID.randomUUID(),
            personnummer = "25678098976",
            saksnummer = ""
        )
        statistikkRepository.lagreBehandling("238909876", oppgave)
        statistikkRepository.lagreBehandling("238909876", oppgave2)
        statistikkRepository.lagreBehandling("238909876", oppgave3)
        statistikkRepository.lagreBehandling("238909876", oppgave4)
        val sistBehandlede = statistikkRepository.hentBehandlinger("238909876")

        assertSame(3, sistBehandlede.size)
        assertSame(1, sistBehandlede.filter { it.saksnummer == "PLUy6" }.size)
    }
}

