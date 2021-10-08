package no.nav.k9.tjenester.saksbehandler.oppgave

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.runBlocking
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.*
import no.nav.k9.domene.repository.*
import no.nav.k9.integrasjon.kafka.dto.Fagsystem
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertSame
import kotlin.test.asserter

class OppgaveTjenesteTest : KoinTest {
    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(buildAndTestConfig())
    }

    @Test
    fun `hent fagsak`() {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveTjeneste = get<OppgaveTjeneste>()

        val oppgave1 = Oppgave(
            behandlingId = 9438,
            fagsakSaksnummer = "Yz647",
            journalpostId = null,
            aktorId = "273857",
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
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
            aksjonspunkter = Aksjonspunkter(emptyMap()),
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

        runBlocking {
            val fagsaker = oppgaveTjeneste.søkFagsaker("Yz647")
            assert(fagsaker.oppgaver.isNotEmpty())
        }
    }


    @Test
    fun skal_test_beholdning() {
        val oppgaveRepository = get<OppgaveRepository>()
        val statistikkRepository = get<StatistikkRepository>()
        val oppgaveTjeneste = get<OppgaveTjeneste>()

        val localDateTime = LocalDate.now().minusDays(2).atStartOfDay()

        val oppgave2DagerSiden = lagOppgave(
            behandlingType = BehandlingType.DIGITAL_ETTERSENDELSE,
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            Fagsystem.PUNSJ.kode,
            localDateTime
        )
        oppgaveRepository.lagre(oppgave2DagerSiden.eksternId) { oppgave2DagerSiden }

        statistikkRepository.lagre(
            AlleOppgaverNyeOgFerdigstilte(
                oppgave2DagerSiden.fagsakYtelseType,
                oppgave2DagerSiden.behandlingType,
                localDateTime.toLocalDate().plusDays(1),
                Fagsystem.fraKode(oppgave2DagerSiden.system)
            )
        ) {
            it.nye.add(oppgave2DagerSiden.eksternId.toString())
            it
        }

        val beholdningPrDag = oppgaveTjeneste.hentBeholdningAvOppgaverPerAntallDager()
    }

    @Test
    fun hentReservasjonsHistorikk() = runBlocking {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val uuid = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = uuid,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(uuid) { oppgaveko }

        val oppgave1 = Oppgave(
            behandlingId = 9438,
            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
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
            aksjonspunkter = Aksjonspunkter(emptyMap()),
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
        oppgaveko.leggOppgaveTilEllerFjernFraKø(oppgave1, reservasjonRepository)
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }


        val oppgaver = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveko.id)
        assert(oppgaver.size == 1)
        val oppgave = oppgaver[0]

        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = "123",
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )
        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = "ny",
                navn = null,
                epost = "test2@test.no",
                enhet = null
            )
        )

        oppgaveTjeneste.reserverOppgave("123", null, oppgave.eksternId)
        oppgaveTjeneste.flyttReservasjon(oppgave.eksternId, "ny", "Ville ikke ha oppgaven")
        val reservasjonsHistorikk = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave.eksternId)

        assert(reservasjonsHistorikk.reservasjoner.size == 2)
        assert(reservasjonsHistorikk.reservasjoner[0].flyttetAv == "saksbehandler@nav.no")
    }

    private fun lagInnslag(
        localDate: LocalDate,
        oppgaveRepository: OppgaveRepository,
        statistikkRepository: StatistikkRepository
    ) {

        for (fagsystem in listOf(Fagsystem.K9SAK, Fagsystem.PUNSJ)) {
            for (behandlingstype in listOf(
                BehandlingType.FORSTEGANGSSOKNAD,
                BehandlingType.DIGITAL_ETTERSENDELSE
            )) {
                for (fagsakytelse in listOf(
                    FagsakYtelseType.OMSORGSDAGER,
                    FagsakYtelseType.PLEIEPENGER_SYKT_BARN
                )) {
                    val oppgave = lagOppgave(
                        behandlingType = behandlingstype,
                        fagsakytelse,
                        fagsystem.kode,
                        localDate.atStartOfDay()
                    )
                    oppgaveRepository.lagre(oppgave.eksternId) { oppgave }

                    statistikkRepository.lagre(
                        AlleOppgaverNyeOgFerdigstilte(
                            oppgave.fagsakYtelseType,
                            oppgave.behandlingType,
                            oppgave.eventTid.toLocalDate(),
                            Fagsystem.fraKode(oppgave.system)
                        )
                    ) {
                        it.nye.add(oppgave.eksternId.toString())
                        it
                    }

                    statistikkRepository.lagre(
                        AlleOppgaverNyeOgFerdigstilte(
                            oppgave.fagsakYtelseType,
                            oppgave.behandlingType,
                            oppgave.eventTid.toLocalDate().plusDays(1),
                            Fagsystem.fraKode(oppgave.system)
                        )
                    ) {
                        it.ferdigstilte.add(oppgave.eksternId.toString())
                        it
                    }
                }
            }
        }
    }

    @Test
    fun skalKunneReserverToOppgaverSamtidig() = runBlocking {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val uuid = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = uuid,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(uuid) { oppgaveko }

        val oppgave1 = Oppgave(
            behandlingId = 9438,
            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
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
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(oppgave1, reservasjonRepository)
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

        val oppgave2 = Oppgave(
            behandlingId = 9430,
            fagsakSaksnummer = "Yz648",
            aktorId = "273858",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
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
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(oppgave2, reservasjonRepository)
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }


        val oppgaver = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveko.id)
        assert(oppgaver.size == 2)
        val oppgave = oppgaver[0]

        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = "123",
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        oppgaveTjeneste.reserverOppgave("123", null, oppgave.eksternId)
        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        val reservasjonsHistorikk2 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave2.eksternId)

        assert(reservasjonsHistorikk1.reservasjoner.size == 1)
        assert(reservasjonsHistorikk1.reservasjoner[0].reservertAv == "123")
        assert(reservasjonsHistorikk2.reservasjoner.size == 1)
        assert(reservasjonsHistorikk2.reservasjoner[0].reservertAv == "123")

        val oppgaverEtterRes = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveko.id)
        asserter.assertEquals("Forventer empty liste", 0, oppgaverEtterRes.size)
    }

    @Test
    fun skalKunneReserverEnOppgaveDerEnAnnenErReservertAlt() = runBlocking {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveTjeneste = get<OppgaveTjeneste>()
        val oppgaveKøRepository = get<OppgaveKøRepository>()
        val reservasjonRepository = get<ReservasjonRepository>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        saksbehandlerRepository.addSaksbehandler(
            Saksbehandler(
                brukerIdent = "123",
                navn = null,
                epost = "test@test.no",
                enhet = null
            )
        )

        val uuid = UUID.randomUUID()
        val oppgaveko = OppgaveKø(
            id = uuid,
            navn = "Ny kø",
            sistEndret = LocalDate.now(),
            sortering = KøSortering.OPPRETT_BEHANDLING,
            filtreringBehandlingTyper = mutableListOf(BehandlingType.FORSTEGANGSSOKNAD, BehandlingType.INNSYN),
            filtreringYtelseTyper = mutableListOf(),
            filtreringAndreKriterierType = mutableListOf(),
            enhet = Enhet.NASJONAL,
            fomDato = null,
            tomDato = null,
            saksbehandlere = mutableListOf()
        )
        oppgaveKøRepository.lagre(uuid) { oppgaveko }

        val oppgave1 = Oppgave(
            behandlingId = 9438,
            fagsakSaksnummer = "Yz647",
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
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
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        oppgaveRepository.lagre(oppgave1.eksternId) { oppgave1 }
        oppgaveko.leggOppgaveTilEllerFjernFraKø(oppgave1, reservasjonRepository)
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }

            oppgaveTjeneste.reserverOppgave("123", null, oppgave1.eksternId)


        val oppgave2 = Oppgave(
            behandlingId = 9430,
            fagsakSaksnummer = "Yz648",
            aktorId = "273858",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
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
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false,
            pleietrengendeAktørId = "273856"
        )
        val hentOppgaveKøer = oppgaveTjeneste.hentOppgaveKøer()
        val oppgaveKø = hentOppgaveKøer[0]

        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }
        oppgaveKø.leggOppgaveTilEllerFjernFraKø(oppgave2, reservasjonRepository)

        oppgaveKøRepository.lagre(oppgaveKø.id) {
            oppgaveKø
        }

        val oppgaver = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveKø.id)
        asserter.assertEquals("Forventer en oppgave her", 1, oppgaver.size)
        val oppgave = oppgaver[0]

        oppgaveTjeneste.reserverOppgave("123", null, oppgave.eksternId)

        val reservasjonsHistorikk1 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave1.eksternId)
        val reservasjonsHistorikk2 = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave2.eksternId)

        assert(reservasjonsHistorikk1.reservasjoner.size == 2)
        assert(reservasjonsHistorikk1.reservasjoner[0].reservertAv == "123")
        assert(reservasjonsHistorikk2.reservasjoner.size == 1)
        assert(reservasjonsHistorikk2.reservasjoner[0].reservertAv == "123")

        val oppgaverEtterRes = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveKø.id)
        asserter.assertEquals("Forventer empty liste", 0, oppgaverEtterRes.size)
    }

    @Test
    fun skal_bare_returnere_aktivte_eller_sist_ikke_aktive_oppgave() = runBlocking {
        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveTjeneste = get<OppgaveTjeneste>()

        val fagsakSaksnummer = "Yz647"
        val oppgave1 = Oppgave(
            behandlingId = 9437,
            fagsakSaksnummer = fagsakSaksnummer,
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
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
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )

        val oppgave2 = Oppgave(
            behandlingId = 9438,
            fagsakSaksnummer = fagsakSaksnummer,
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = LocalDateTime.now(),
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = BehandlingType.FORSTEGANGSSOKNAD,
            fagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            aktiv = false,
            system = "K9SAK",
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap()),
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
        oppgaveRepository.lagre(oppgave2.eksternId) { oppgave2 }

        val saker = oppgaveTjeneste.søkFagsaker(fagsakSaksnummer)
        assertThat(saker.oppgaver.size).isEqualTo(1)
    }


    private fun lagOppgave(
        behandlingType: BehandlingType,
        fagsakYtelseType: FagsakYtelseType,
        fagsystem: String,
        localDateTime: LocalDateTime
    ): Oppgave {
        return Oppgave(
            behandlingId = 9437,
            fagsakSaksnummer = UUID.randomUUID().toString(),
            aktorId = "273857",
            journalpostId = null,
            behandlendeEnhet = "Enhet",
            behandlingsfrist = LocalDateTime.now(),
            behandlingOpprettet = localDateTime,
            forsteStonadsdag = LocalDate.now().plusDays(6),
            behandlingStatus = BehandlingStatus.OPPRETTET,
            behandlingType = behandlingType,
            fagsakYtelseType = fagsakYtelseType,
            aktiv = true,
            system = fagsystem,
            oppgaveAvsluttet = null,
            utfortFraAdmin = false,
            eksternId = UUID.randomUUID(),
            oppgaveEgenskap = emptyList(),
            aksjonspunkter = Aksjonspunkter(emptyMap()),
            tilBeslutter = true,
            utbetalingTilBruker = false,
            selvstendigFrilans = false,
            kombinert = false,
            søktGradering = false,
            årskvantum = false,
            avklarArbeidsforhold = false,
            avklarMedlemskap = false, kode6 = false, utenlands = false, vurderopptjeningsvilkåret = false
        )
    }
}
