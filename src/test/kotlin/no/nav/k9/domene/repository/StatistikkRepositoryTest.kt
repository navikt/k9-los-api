package no.nav.k9.domene.repository

import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.integrasjon.kafka.dto.Fagsystem
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.tjenester.saksbehandler.oppgave.BehandletOppgave
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StatistikkRepositoryTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(buildAndTestConfig())
    }

    @Test
    fun skalFylleMedTommeElementerDersomViIkkeHarDataPåDenDagen() {

        val statistikkRepository  = get<StatistikkRepository>()

        val hentFerdigstilte = statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker()

        val omsorgspenger = hentFerdigstilte.take(FagsakYtelseType.values().size*5).filter { it.fagsakYtelseType == FagsakYtelseType.OMSORGSPENGER }
        assertSame(5, omsorgspenger.size)
        stopKoin()
    }

    @Test
    fun test_hentFerdigstilteOgNyeHistorikkPerAntallDager() {
        val statistikkRepository  = get<StatistikkRepository>()
        val oppgave = lagOppgave(
            BehandlingType.FORSTEGANGSSOKNAD,
            FagsakYtelseType.OMSORGSPENGER,
            LocalDateTime.now().minusDays(23)
        )
        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType, oppgave.eventTid.toLocalDate().minusDays(1), Fagsystem.K9SAK)){
            it.nye.add(oppgave.eksternId.toString())
            it
        }

        val hentFerdigstilteOgNyeHistorikkPerAntallDager =
            statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(10)

        assertSame(1, hentFerdigstilteOgNyeHistorikkPerAntallDager.size)
    }

    @Test
    fun test_fjernDataFraSystem() {
        val statistikkRepository  = get<StatistikkRepository>()
        val oppgave = lagOppgave(
            BehandlingType.FORSTEGANGSSOKNAD,
            FagsakYtelseType.OMSORGSPENGER,
            LocalDateTime.now().minusDays(23)
        )
        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType, oppgave.eventTid.toLocalDate().minusDays(1), Fagsystem.PUNSJ)){
            it.nye.add(oppgave.eksternId.toString())
            it
        }

        val antallRader = statistikkRepository.fjernDataFraSystem(Fagsystem.PUNSJ)
        assertSame(1, antallRader)

        val hentFerdigstilteOgNyeHistorikkPerAntallDager =
            statistikkRepository.hentFerdigstilteOgNyeHistorikkPerAntallDager(10)

        assertSame(0, hentFerdigstilteOgNyeHistorikkPerAntallDager.size)
    }

    @Test
    fun test_hentFerdigstilteOgNyeHistorikkSiste8Uker() {
        val statistikkRepository  = get<StatistikkRepository>()
        val oppgave = lagOppgave(
            BehandlingType.FORSTEGANGSSOKNAD,
            FagsakYtelseType.OMSORGSPENGER,
            LocalDateTime.now().minusDays(23)
        )
        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType, oppgave.eventTid.toLocalDate().minusDays(1), Fagsystem.K9SAK)){
            it.nye.add(oppgave.eksternId.toString())
            it
        }

        val hentFerdigstilteOgNyeHistorikkPerAntallDager =
            statistikkRepository.hentFerdigstilteOgNyeHistorikkSiste8Uker()

        assertSame(1, hentFerdigstilteOgNyeHistorikkPerAntallDager.size)
    }


    @Test
    fun skalGiRiktigAntallIBeholding() {
        val oppgaveRepository  = get<OppgaveRepository>()
        val statistikkRepository  = get<StatistikkRepository>()
        val oppgaveTjeneste  = get<OppgaveTjeneste>()

        val iGår = LocalDate.now().minusDays(1)
        val toDagerSiden = LocalDate.now().minusDays(2)
        val treDagerSiden = LocalDate.now().minusDays(3)
        val fireDagerSiden = LocalDate.now().minusDays(4)
        val femDagerSiden = LocalDate.now().minusDays(5)
        val seksDagerSiden = LocalDate.now().minusDays(6)

        val oppgaveDag6 = lagOppgave(
            BehandlingType.FORSTEGANGSSOKNAD,
            FagsakYtelseType.OMSORGSPENGER,
            seksDagerSiden.atStartOfDay()
        )

        // 6dager siden
        oppgaveTilkommer(oppgaveDag6, oppgaveRepository, statistikkRepository)

        // 5 dager siden
        val oppgaveDag5_1 = lagOppgave(
            BehandlingType.FORSTEGANGSSOKNAD,
            FagsakYtelseType.OMSORGSPENGER,
            femDagerSiden.atStartOfDay()
        )
        val oppgaveDag5_2 = lagOppgave(
            BehandlingType.FORSTEGANGSSOKNAD,
            FagsakYtelseType.OMSORGSPENGER,
            femDagerSiden.atStartOfDay()
        )

        oppgaveTilkommer(oppgaveDag5_1, oppgaveRepository, statistikkRepository)
        oppgaveTilkommer(oppgaveDag5_2, oppgaveRepository, statistikkRepository)

        // 4 dager siden
        oppgaveFerdig(oppgaveDag6, fireDagerSiden, oppgaveRepository, statistikkRepository)
        oppgaveFerdig(oppgaveDag5_1, fireDagerSiden, oppgaveRepository, statistikkRepository)

        // 3 dager siden
        oppgaveFerdig(oppgaveDag5_2, treDagerSiden, oppgaveRepository, statistikkRepository)

        // 2 dager siden
        val oppgaveDag2_1 = lagOppgave(
            BehandlingType.FORSTEGANGSSOKNAD,
            FagsakYtelseType.OMSORGSPENGER,
            toDagerSiden.atStartOfDay()
        )

        oppgaveTilkommer(oppgaveDag2_1, oppgaveRepository, statistikkRepository)

        // i går
        val oppgaveDag1_1 = lagOppgave(
            BehandlingType.FORSTEGANGSSOKNAD,
            FagsakYtelseType.OMSORGSPENGER,
            iGår.atStartOfDay()
        )

        oppgaveTilkommer(oppgaveDag1_1, oppgaveRepository, statistikkRepository)

        val dagerMedData = oppgaveTjeneste.hentBeholdningAvOppgaverPerAntallDager().filter { it.fagsakYtelseType == FagsakYtelseType.OMSORGSPENGER && it.behandlingType == BehandlingType.FORSTEGANGSSOKNAD  }

        assertSame( 2, dagerMedData.filter { it.dato == LocalDate.now() }[0].antall)
        assertSame( 1, dagerMedData.filter { it.dato == iGår }[0].antall)
        assertSame( 0, dagerMedData.filter { it.dato == toDagerSiden }[0].antall)
        assertSame( 1, dagerMedData.filter { it.dato == treDagerSiden }[0].antall)
        assertSame( 3, dagerMedData.filter { it.dato == fireDagerSiden }[0].antall)
        assertSame( 1, dagerMedData.filter { it.dato == femDagerSiden }[0].antall)
        assertSame( 0, dagerMedData.filter { it.dato == seksDagerSiden }[0].antall)
    }

    private fun oppgaveTilkommer(
        oppgave: Oppgave,
        oppgaveRepository: OppgaveRepository,
        statistikkRepository: StatistikkRepository
    ) {
        oppgaveRepository.lagre(oppgave.eksternId) {
            oppgave
        }
        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType, oppgave.behandlingOpprettet.toLocalDate(), Fagsystem.K9SAK)){
            it.nye.add(oppgave.eksternId.toString())
            it
        }

    }

    private fun oppgaveFerdig(
        oppgaveDag6: Oppgave,
        ferdigstilles: LocalDate,
        oppgaveRepository: OppgaveRepository,
        statistikkRepository: StatistikkRepository
    ) {
        val settOppgaveTilInaktiv = settOppgaveTilInaktiv(oppgaveDag6)
        oppgaveRepository.lagre(settOppgaveTilInaktiv.eksternId) {
            settOppgaveTilInaktiv
        }
        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(settOppgaveTilInaktiv.fagsakYtelseType, settOppgaveTilInaktiv.behandlingType, ferdigstilles, Fagsystem.K9SAK)){
            it.ferdigstilte.add(settOppgaveTilInaktiv.eksternId.toString())
            it
        }
    }

    @Test
    fun skalFylleMedTommeElementerDersomVdiIkkeHarDataPåDenDagenIdempotent() {

        val statistikkRepository  = get<StatistikkRepository>()

        val oppgave = lagOppgave(
            BehandlingType.FORSTEGANGSSOKNAD,
            FagsakYtelseType.OMSORGSPENGER,
            LocalDateTime.now().minusDays(23)
        )
        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType, oppgave.eventTid.toLocalDate().minusDays(1), Fagsystem.K9SAK)){
            it.nye.add(oppgave.eksternId.toString())
            it
        }
        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType, oppgave.eventTid.toLocalDate().minusDays(1), Fagsystem.K9SAK)){
            it.nye.add(oppgave.eksternId.toString())
            it
        }
        val hentFerdigstilte = statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker()
        val omsorgspenger = hentFerdigstilte.reversed().filter { it.fagsakYtelseType == FagsakYtelseType.OMSORGSPENGER }
        assertSame(1, omsorgspenger.find { it.behandlingType == BehandlingType.FORSTEGANGSSOKNAD && it.kilde == Fagsystem.K9SAK }?.nye?.size )

    }

    private fun lagOppgave(
        behandlingType: BehandlingType, fagsakYtelseType: FagsakYtelseType, localDateTime: LocalDateTime
    ): Oppgave {
        return Oppgave(
            behandlingId = 78567,
            fagsakSaksnummer = "5Yagdt",
            aktorId = "675864",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = localDateTime,
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = behandlingType,
            fagsakYtelseType = fagsakYtelseType,
            aktiv = true,
            system = "K9SAK",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = true,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
    }

    private fun settOppgaveTilInaktiv(oppgave: Oppgave): Oppgave {
        return Oppgave(
            behandlingId = oppgave.behandlingId,
            fagsakSaksnummer = oppgave.fagsakSaksnummer,
            aktorId = oppgave.aktorId,
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = oppgave.behandlingOpprettet,
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = oppgave.behandlingType,
            fagsakYtelseType = oppgave.fagsakYtelseType,
            aktiv = false,
            system = "K9SAK",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = oppgave.eksternId,
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = true,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
    }

    @Test
    fun skalLagreNedMedPunsjSomSystem() {

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
            behandlingType = BehandlingType.DIGITAL_ETTERSENDELSE,
            fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            aktiv = true,
            system = "PUNSJ",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = true,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
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
            fagsakYtelseType = FagsakYtelseType.OMSORGSPENGER,
            aktiv = true,
            system = "PUNSJ",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = true,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )

        val dato = oppgave.eventTid.toLocalDate().minusDays(1)


        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType,
            dato, Fagsystem.PUNSJ)){
            it.nye.add(oppgave.eksternId.toString())
            it
        }

        statistikkRepository.lagre(AlleOppgaverNyeOgFerdigstilte(oppgave.fagsakYtelseType, oppgave.behandlingType,
            dato.minusDays(1), Fagsystem.PUNSJ)){
            it.nye.add(oppgave.eksternId.toString())
            it
        }

        val hentFerdigstilte = statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker()

        val list = hentFerdigstilte.groupBy { it.dato }[dato]?.groupBy { (it.kilde) }?.get(Fagsystem.PUNSJ)

        assertTrue(list?.filter { it.nye.isNotEmpty() }?.size == 1)

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
        statistikkRepository.lagreBehandling("238909876"){
            oppgave
        }
        statistikkRepository.lagreBehandling("238909876"){
            oppgave2
        }
        statistikkRepository.lagreBehandling("238909876"){
            oppgave3
        }
        statistikkRepository.lagreBehandling("238909876"){
            oppgave4
        }
        val sistBehandlede = statistikkRepository.hentBehandlinger("238909876")

        assertSame(3, sistBehandlede.size)
        assertSame(1, sistBehandlede.filter { it.saksnummer == "PLUy6" }.size)
    }
}

