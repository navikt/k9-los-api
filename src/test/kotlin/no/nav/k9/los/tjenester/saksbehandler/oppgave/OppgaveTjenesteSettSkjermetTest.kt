package no.nav.k9.los.tjenester.saksbehandler.oppgave

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractPostgresTest
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.buildAndTestConfig
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.db.TransactionalManager
import no.nav.k9.los.domene.modell.Aksjonspunkter
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Enhet
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.KøSortering
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.PepClientLocal
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.AzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PersonPdlResponse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class OppgaveTjenesteSettSkjermetTest : KoinTest, AbstractPostgresTest() {

    @BeforeEach
    fun setup() {
        val områdeSetup = get<OmrådeSetup>()
        områdeSetup.setup()
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        k9SakTilLosAdapterTjeneste.setup()
    }

    @JvmField
    @RegisterExtension
    val koinTestRule = KoinTestExtension.create {
        modules(buildAndTestConfig(dataSource, mockk()))
    }

    @Test
    fun testSettSkjermet() = runBlocking{

        val pepClient = get<IPepClient>()

        coEvery { pepClient.harTilgangTilKode6() } returns false
        coEvery { pepClient.erSakKode6(any()) } returns false
        coEvery { pepClient.erSakKode7EllerEgenAnsatt(any()) } returns false
        val oppgaveKøOppdatert = Channel<UUID>(1)
        val oppgaveRefreshOppdatert = Channel<UUID>(100)
        val statistikkChannel = Channel<Boolean>(Channel.CONFLATED)

        val oppgaveRepository = get<OppgaveRepository>()
        val oppgaveRepositoryV2 = get<OppgaveRepositoryV2>()
        val oppgaverGruppertRepository = get<OppgaverGruppertRepository>()

        val oppgaveKøRepository = OppgaveKøRepository(
            dataSource = get(),
            oppgaveRepositoryV2,
            oppgaveKøOppdatert = oppgaveKøOppdatert,
            oppgaveRefreshChannel = oppgaveRefreshOppdatert,
            pepClient = pepClient
        )
        val saksbehandlerRepository = SaksbehandlerRepository(dataSource = get(),
            pepClient = pepClient)
        val reservasjonRepository = ReservasjonRepository(
            oppgaveKøRepository = oppgaveKøRepository,
            oppgaveRepository = oppgaveRepository,
            oppgaveRepositoryV2 = oppgaveRepositoryV2,
            saksbehandlerRepository = saksbehandlerRepository,
            dataSource = get()
        )

        val reservasjonOversetter = mockk<ReservasjonOversetter>()
        every { reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(any()) } returns null

        val config = mockk<Configuration>()
        val pdlService = mockk<PdlService>()
        val statistikkRepository = StatistikkRepository(dataSource = get())

        val azureGraphService = mockk<AzureGraphService>()
        val oppgaveTjeneste = OppgaveTjeneste(
            oppgaveRepository,
            oppgaverGruppertRepository,
            oppgaveKøRepository,
            saksbehandlerRepository,
            pdlService,
            reservasjonRepository, config, azureGraphService, pepClient, statistikkRepository, reservasjonOversetter, statistikkChannel, KoinProfile.LOCAL
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
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }
        every { config.koinProfile() } returns KoinProfile.LOCAL
        coEvery { pdlService.person(any()) } returns PersonPdlResponse(false, PersonPdl(
            data = PersonPdl.Data(
                hentPerson = PersonPdl.Data.HentPerson(
                    listOf(
                        element =
                        PersonPdl.Data.HentPerson.Folkeregisteridentifikator("012345678901")
                    ),
                    navn = listOf(
                        PersonPdl.Data.HentPerson.Navn(
                            etternavn = "Etternavn",
                            forkortetNavn = "ForkortetNavn",
                            fornavn = "Fornavn",
                            mellomnavn = null
                        )
                    ),
                    kjoenn = listOf(
                        PersonPdl.Data.HentPerson.Kjoenn(
                            "KVINNE"
                        )
                    ),
                    doedsfall = emptyList()
                )
            )
        ))
        coEvery { pepClient.harBasisTilgang() } returns true
        coEvery { pepClient.harTilgangTilOppgave(any()) } returns true

        var oppgaver = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveko.id)
        assert(oppgaver.size == 1)

        coEvery { pepClient.erSakKode7EllerEgenAnsatt(any()) } returns true

        oppgaveTjeneste.settSkjermet(oppgave1)

        oppgaver = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveko.id)
        assert(oppgaver.isEmpty())

    }

    @Test
    fun `hent fagsak`(){
        val oppgaveKøOppdatert = Channel<UUID>(1)
        val oppgaverRefresh = Channel<UUID>(1000)
        val statistikkChannel = Channel<Boolean>(Channel.CONFLATED)

        val oppgaveRepository = OppgaveRepository(dataSource = dataSource,pepClient = PepClientLocal(), refreshOppgave = oppgaverRefresh)
        val oppgaveRepositoryV2 = OppgaveRepositoryV2(dataSource = dataSource)
        val oppgaverGruppertRepository = get<OppgaverGruppertRepository>()

        val oppgaveKøRepository = OppgaveKøRepository(
            dataSource = dataSource,
            oppgaveRepositoryV2 = oppgaveRepositoryV2,
            oppgaveKøOppdatert = oppgaveKøOppdatert,
            oppgaveRefreshChannel = oppgaverRefresh,
            pepClient = PepClientLocal()
        )
        val pdlService = mockk<PdlService>()
        val saksbehandlerRepository = SaksbehandlerRepository(dataSource = dataSource,
            pepClient = PepClientLocal()
        )

        val statistikkRepository = StatistikkRepository(dataSource = dataSource)
        val pepClient = mockk<IPepClient>()
        val azureGraphService = mockk<AzureGraphService>()
        val config = mockk<Configuration>()
        val reservasjonRepository = ReservasjonRepository(
            oppgaveKøRepository = oppgaveKøRepository,
            oppgaveRepository = oppgaveRepository,
            oppgaveRepositoryV2 = oppgaveRepositoryV2,
            saksbehandlerRepository = saksbehandlerRepository,
            dataSource = dataSource
        )
        val reservasjonOversetter = get<ReservasjonOversetter>()
        val oppgaveTjeneste = OppgaveTjeneste(
            oppgaveRepository,
            oppgaverGruppertRepository,
            oppgaveKøRepository,
            saksbehandlerRepository,
            pdlService,
            reservasjonRepository, config, azureGraphService, pepClient, statistikkRepository, reservasjonOversetter, statistikkChannel, KoinProfile.LOCAL
        )

        val oppgave1 = Oppgave(
            
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
        get<K9SakTilLosAdapterTjeneste>()
        val oppgaveV3Tjeneste = get<OppgaveV3Tjeneste>()

        val transactionalManager = get<TransactionalManager>()
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(lagOppgaveDto(oppgave1.eksternId.toString()), tx)
        }

        coEvery {  azureGraphService.hentIdentTilInnloggetBruker() } returns "123"
        every { config.koinProfile() } returns KoinProfile.LOCAL
        coEvery { pepClient.harTilgangTilOppgave(any()) } returns true
        coEvery { pdlService.person(any()) } returns PersonPdlResponse(false, PersonPdl(data = PersonPdl.Data(
            hentPerson = PersonPdl.Data.HentPerson(
                folkeregisteridentifikator = listOf(PersonPdl.Data.HentPerson.Folkeregisteridentifikator("12345678901")),
                navn = listOf(
                    PersonPdl.Data.HentPerson.Navn(
                        etternavn = "etternavn",
                        forkortetNavn = null,
                        fornavn = "fornavn",
                        mellomnavn = null
                    )),
                kjoenn = listOf(PersonPdl.Data.HentPerson.Kjoenn("K")),
                doedsfall = listOf()
            )
        )))

        runBlocking {
            val fagsaker = oppgaveTjeneste.søkFagsaker("Yz647")
            assert(fagsaker.oppgaver.isNotEmpty())
        }
    }

    private fun lagOppgaveDto(eksternId: String): OppgaveDto {
        return OppgaveDto(
            id = eksternId,
            versjon = LocalDateTime.now().toString(),
            område = Område(eksternId = "K9").eksternId,
            kildeområde = "K9",
            type = "k9sak",
            status = "AAPEN",
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = "test",
            feltverdier = listOf(
                OppgaveFeltverdiDto(
                    nøkkel = "aksjonspunkt",
                    verdi = "9001"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "aktorId",
                    verdi = "SKAL IKKE LOGGES"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "utenlandstilsnitt",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "resultattype",
                    verdi = "test"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "behandlingsstatus",
                    verdi = "test"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "behandlingTypekode",
                    verdi = "test"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "totrinnskontroll",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "ytelsestype",
                    verdi = "test"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "saksnummer",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "hastesak",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "behandlingUuid",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "fagsystem",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerAnnet",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerSaksbehandler",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerAnnetIkkeSaksbehandlingstid",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerArbeidsgiver",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "helautomatiskBehandlet",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerTekniskFeil",
                    verdi = "false"
                ),
                OppgaveFeltverdiDto(
                    nøkkel = "avventerSøker",
                    verdi = "false"
                ),
            )
        )
    }

    @Test
    fun hentReservasjonsHistorikk() = runBlocking {
        val oppgaveKøOppdatert = Channel<UUID>(1)

        val oppgaverRefresh = Channel<UUID>(1000)
        val statistikkChannel = Channel<Boolean>(Channel.CONFLATED)

        val oppgaveRepository = OppgaveRepository(dataSource = dataSource,pepClient = PepClientLocal(), refreshOppgave = oppgaverRefresh)
        val oppgaveRepositoryV2 = OppgaveRepositoryV2(dataSource = dataSource)
        val oppgaverGruppertRepository = get<OppgaverGruppertRepository>()

        val oppgaveKøRepository = OppgaveKøRepository(
            dataSource = dataSource,
            oppgaveRepositoryV2 = oppgaveRepositoryV2,
            oppgaveKøOppdatert = oppgaveKøOppdatert,
            oppgaveRefreshChannel = oppgaverRefresh,
            pepClient = PepClientLocal()
        )
        val saksbehandlerRepository = SaksbehandlerRepository(dataSource = dataSource,
            pepClient = PepClientLocal()
        )
        val reservasjonRepository = ReservasjonRepository(
            oppgaveKøRepository = oppgaveKøRepository,
            oppgaveRepository = oppgaveRepository,
            oppgaveRepositoryV2 = oppgaveRepositoryV2,
            saksbehandlerRepository = saksbehandlerRepository,
            dataSource = dataSource
        )

        val reservasjonOversetter = mockk<ReservasjonOversetter>()
        every { reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(any()) } returns null
        val config = mockk<Configuration>()
        val pdlService = mockk<PdlService>()
        val statistikkRepository = StatistikkRepository(dataSource = dataSource)
        val pepClient = mockk<IPepClient>()
        val azureGraphService = mockk<AzureGraphService>()

        coEvery {  azureGraphService.hentIdentTilInnloggetBruker() } returns "123"
        val oppgaveTjeneste = OppgaveTjeneste(
            oppgaveRepository,
            oppgaverGruppertRepository,
            oppgaveKøRepository,
            saksbehandlerRepository,
            pdlService,
            reservasjonRepository, config, azureGraphService, pepClient, statistikkRepository, reservasjonOversetter, statistikkChannel, KoinProfile.LOCAL
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
        oppgaveko.leggOppgaveTilEllerFjernFraKø(
            oppgave1,
            reservasjonRepository
        )
        oppgaveKøRepository.lagre(oppgaveko.id) {
            oppgaveko
        }
        every { config.koinProfile() } returns KoinProfile.LOCAL
        coEvery { pepClient.harBasisTilgang() } returns true
        coEvery { pepClient.harTilgangTilOppgave(any()) } returns true
        coEvery { pepClient.harTilgangTilReserveringAvOppgaver() } returns true
        coEvery { pdlService.person(any()) } returns PersonPdlResponse(false, PersonPdl(
            data = PersonPdl.Data(
                hentPerson = PersonPdl.Data.HentPerson(
                    listOf(
                        element =
                        PersonPdl.Data.HentPerson.Folkeregisteridentifikator("012345678901")
                    ),
                    navn = listOf(
                        PersonPdl.Data.HentPerson.Navn(
                            etternavn = "Etternavn",
                            forkortetNavn = "ForkortetNavn",
                            fornavn = "Fornavn",
                            mellomnavn = null
                        )
                    ),
                    kjoenn = listOf(
                        PersonPdl.Data.HentPerson.Kjoenn(
                            "KVINNE"
                        )
                    ),
                    doedsfall = emptyList()
                )
            )
        ))


        val oppgaver = oppgaveTjeneste.hentNesteOppgaverIKø(oppgaveko.id)
        assert(oppgaver.size == 1)
        val oppgave = oppgaver[0]

        saksbehandlerRepository.addSaksbehandler(Saksbehandler(null, brukerIdent = "123", navn= null, epost = "test@test.no", enhet = null))
        saksbehandlerRepository.addSaksbehandler(Saksbehandler(null, brukerIdent="ny", navn=null,epost =  "test2@test.no",enhet = null))

        oppgaveTjeneste.reserverOppgave("123", null, oppgave.eksternId)
        oppgaveTjeneste.flyttReservasjon(oppgave.eksternId, "ny", "Ville ikke ha oppgaven")
        val reservasjonsHistorikk = oppgaveTjeneste.hentReservasjonsHistorikk(oppgave.eksternId)

        assert(reservasjonsHistorikk.reservasjoner.size == 2)
        assert(reservasjonsHistorikk.reservasjoner[0].flyttetAv == "123")
    }

}
