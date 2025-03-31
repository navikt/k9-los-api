@file:Suppress("USELESS_CAST")

package no.nav.k9.los

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.channels.Channel
import no.nav.k9.los.aksjonspunktbehandling.K9KlageEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9TilbakeEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.eventhandler.RefreshK9v3Tjeneste
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.abac.PepClientLocal
import no.nav.k9.los.integrasjon.azuregraph.AzureGraphServiceLocal
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.k9.IK9SakService
import no.nav.k9.los.integrasjon.k9.K9SakServiceLocal
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.pdl.PdlServiceLocal
import no.nav.k9.los.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.beriker.K9KlageBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.beriker.K9KlageBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.k9TilbakeEksternId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonKonverteringJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.forvaltning.ForvaltningRepository
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.PartisjonertOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheService
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.søkeboks.SøkeboksTjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.NøkkeltallRepositoryV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.status.StatusService
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederTjeneste
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.NokkeltallTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.*
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

fun buildAndTestConfig(dataSource: DataSource, pepClient: IPepClient = PepClientLocal()): Module = module {

    val config = mockk<Configuration>()

    single(named("oppgaveKøOppdatert")) {
        Channel<UUID>(Channel.UNLIMITED)
    }
    single(named("oppgaveChannel")) {
        Channel<Oppgave>(Channel.UNLIMITED)
    }
    single(named("oppgaveRefreshChannel")) {
        Channel<Oppgave>(Channel.UNLIMITED)
    }
    single(named("KøpåvirkendeHendelseChannel")) {
        Channel<KøpåvirkendeHendelse>(Channel.UNLIMITED)
    }
    single(named("statistikkRefreshChannel")) {
        Channel<Boolean>(Channel.CONFLATED)
    }
    single(named("historikkvaskChannelK9Sak")) {
        Channel<Boolean>(Channel.UNLIMITED)
    }
    single(named("historikkvaskChannelK9Tilbake")) {
        Channel<k9TilbakeEksternId>(Channel.UNLIMITED)
    }

    single {
        K9SakServiceLocal() as IK9SakService
    }

    single { PepCacheRepository(dataSource) }
    single {
        PepCacheService(
            pepClient = get(),
            pepCacheRepository = get(),
            oppgaveRepository = get(),
            transactionalManager = get()
        )
    }

    single { dataSource }
    single { pepClient }
    single {
        OppgaveRepository(
            dataSource = get(),
            pepClient = get(),
            refreshOppgave = get(named("oppgaveRefreshChannel"))
        )
    }
    single {
        AktivOppgaveRepository(
            oppgavetypeRepository = get()
        )
    }

    single { DriftsmeldingRepository(get()) }
    single { no.nav.k9.los.domene.repository.StatistikkRepository(get()) }

    single {
        OppgaveKøRepository(
            dataSource = get(),
            oppgaveRepositoryV2 = get(),
            oppgaveKøOppdatert = get(named("oppgaveKøOppdatert")),
            oppgaveRefreshChannel = get(named("oppgaveRefreshChannel")),
            pepClient = get()
        )
    }
    single {
        SaksbehandlerRepository(
            dataSource = get(),
            pepClient = get()
        )
    }

    single {
        GyldigeFeltutledere(
            saksbehandlerRepository = get()
        )
    }

    single {
        ReservasjonRepository(
            oppgaveKøRepository = get(),
            oppgaveRepository = get(),
            oppgaveRepositoryV2 = get(),
            saksbehandlerRepository = get(),
            dataSource = get()
        )
    }

    single {
        config
    }
    every { config.koinProfile() } returns KoinProfile.LOCAL
    every { config.auditEnabled() } returns false
    every { config.auditVendor() } returns "k9"
    every { config.auditProduct() } returns "k9-los-api"
    every { config.k9FrontendUrl() } returns "http://localhost:9000"
    every { config.k9PunsjFrontendUrl() } returns "http://localhost:8080"
    every { config.nyOppgavestyringAktivert() } returns true

    single {
        PdlServiceLocal() as IPdlService
    }
    single {
        AzureGraphServiceLocal(
        ) as IAzureGraphService
    }

    val reservasjonOversetterMock = mockk<ReservasjonOversetter>()
    every {
        reservasjonOversetterMock.taNyReservasjonFraGammelKontekst(any(), any(), any(), any(), any())
    } returns ReservasjonV3(
        reservertAv = 123,
        reservasjonsnøkkel = "test1",
        gyldigFra = LocalDateTime.now(),
        gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
        kommentar = "",
        endretAv = null
    )
    every {
        reservasjonOversetterMock.hentAktivReservasjonFraGammelKontekst(any())
    } returns ReservasjonV3(
        reservertAv = 1,
        reservasjonsnøkkel = "test1",
        gyldigFra = LocalDateTime.now(),
        gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
        kommentar = "",
        endretAv = null
    )

    single {
        ReservasjonKonverteringJobb(
            config = get(),
            reservasjonV3Tjeneste = get(),
            transactionalManager = get(),
            oppgaveRepository = get(),
        )
    }

    single {
        OppgaveTjeneste(
            oppgaveRepository = get(),
            oppgaverGruppertRepository = get(),
            oppgaveKøRepository = get(),
            saksbehandlerRepository = get(),
            pdlService = get(),
            reservasjonRepository = get(),
            configuration = get(),
            azureGraphService = get(),
            pepClient = get(),
            statistikkRepository = get(),
            reservasjonOversetter = reservasjonOversetterMock,
            statistikkChannel = get(named("statistikkRefreshChannel")),
            koinProfile = config.koinProfile(),
        )
    }


    single {
        ReservasjonOversetter(
            transactionalManager = get(),
            oppgaveV3Repository = get(),
            reservasjonV3Tjeneste = get(),
            oppgaveV1Repository = get(),
            oppgaveV3RepositoryMedTxWrapper = get(),
        )
    }

    single { OppgaveRepositoryV2(dataSource = get()) }
    single { TransactionalManager(dataSource = get()) }
    single {
        OppgaveTjenesteV2(
            oppgaveRepository = get(),
            migreringstjeneste = get(),
            tm = get()
        )
    }

    single { BehandlingsmigreringTjeneste(BehandlingProsessEventK9Repository(dataSource = get())) }

    single {
        NokkeltallTjeneste(
            pepClient = get(),
            oppgaverGruppertRepository = get(),
            oppgaveRepository = get(),
            statistikkRepository = get(),
            nøkkeltallRepository = get(),
            nøkkeltallRepositoryV3 = get(),
            koinProfile = config.koinProfile(),
        )
    }

    val sakOgBehadlingProducer = mockk<SakOgBehandlingProducer>()
    every { sakOgBehadlingProducer.behandlingOpprettet(any()) } just runs
    every { sakOgBehadlingProducer.avsluttetBehandling(any()) } just runs

    single {
        K9sakEventHandler(
            get(),
            BehandlingProsessEventK9Repository(dataSource = get()),
            sakOgBehandlingProducer = sakOgBehadlingProducer,
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            statistikkRepository = get(),
            reservasjonTjeneste = get(),
            k9SakTilLosAdapterTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single {
        K9KlageEventHandler(
            BehandlingProsessEventKlageRepository(dataSource = get()),
            k9KlageTilLosAdapterTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single {
        K9TilbakeEventHandler(
            get(),
            BehandlingProsessEventTilbakeRepository(dataSource = get()),
            sakOgBehandlingProducer = sakOgBehadlingProducer,
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            statistikkRepository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            reservasjonTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
            k9TilbakeTilLosAdapterTjeneste = get(),
        )
    }

    single {
        K9punsjEventHandler(
            oppgaveRepository = get(),
            oppgaveTjenesteV2 = get(),
            punsjEventK9Repository = get(),
            statistikkChannel = get(),
            reservasjonRepository = get(),
            oppgaveKøRepository = get(),
            reservasjonTjeneste = get(),
            statistikkRepository = get(),
            azureGraphService = get(),
            punsjTilLosAdapterTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single {
        ReservasjonTjeneste(
            reservasjonRepository = get(),
            saksbehandlerRepository = get()
        )
    }

    single {
        K9punsjEventHandler(
            oppgaveRepository = get(),
            oppgaveTjenesteV2 = get(),
            punsjEventK9Repository = PunsjEventK9Repository(dataSource = get()),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            reservasjonTjeneste = get(),
            statistikkRepository = get(),
            azureGraphService = get(),
            punsjTilLosAdapterTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single { OppgaveKøOppdaterer(get(), get(), get()) }

    single {
        AvdelingslederTjeneste(
            transactionalManager = get(),
            oppgaveKøRepository = get(),
            oppgaveKøV3Repository = get(),
            saksbehandlerRepository = get(),
            oppgaveTjeneste = get(),
            pepClient = get(),
            reservasjonV3Tjeneste = get(),
        )
    }

    single { FeltdefinisjonRepository(områdeRepository = get()) }
    single { OmrådeRepository(dataSource = get()) }
    single(createdAtStart = true) {
        OmrådeSetup(
            områdeRepository = get(),
            feltdefinisjonTjeneste = get()
        )
    }
    single { OppgavetypeRepository(dataSource = get(), feltdefinisjonRepository = get(), områdeRepository = get(), gyldigeFeltutledere = get()) }
    single { OppgaveV3Repository(dataSource = get(), oppgavetypeRepository = get()) }
    single { PartisjonertOppgaveRepository(oppgavetypeRepository = get()) }
    single { BehandlingProsessEventK9Repository(dataSource = get()) }
    single { BehandlingProsessEventKlageRepository(dataSource = get()) }
    single { PunsjEventK9Repository(dataSource = get()) }
    single { K9SakOppgaveTilDVHMapper() }
    single { K9KlageOppgaveTilDVHMapper() }
    single { OppgaveRepository(oppgavetypeRepository = get()) }
    single {
        StatistikkRepository(
            dataSource = get(),
            oppgavetypeRepository = get()
        )
    }
    single { NøkkeltallRepository(dataSource = get()) }

    single { NøkkeltallRepositoryV3(dataSource = get()) }

    single { OppgaverGruppertRepository(dataSource = get()) }

    single { mockk<StatistikkPublisher>() }

    single {
        OppgavestatistikkTjeneste(
            oppgavetypeRepository = get(),
            statistikkPublisher = get(),
            transactionalManager = get(),
            statistikkRepository = get(),
            pepClient = get(),
            config = get()
        )
    }

    single {
        FeltdefinisjonTjeneste(
            feltdefinisjonRepository = get(),
            områdeRepository = get(),
            transactionalManager = get()
        )
    }
    single {
        OppgaveV3Tjeneste(
            oppgaveV3Repository = get(),
            partisjonertOppgaveRepository = get(),
            oppgavetypeRepository = get(),
            områdeRepository = get(),
        )
    }
    single {
        OppgavetypeTjeneste(
            oppgavetypeRepository = get(),
            områdeRepository = get(),
            feltdefinisjonRepository = get(),
            transactionalManager = get(),
            gyldigeFeltutledere = get()
        )
    }

    single {
        K9SakTilLosAdapterTjeneste(
            behandlingProsessEventK9Repository = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            k9SakBerikerKlient = get(),
            pepCacheService = get(),
            oppgaveRepository = get(),
            reservasjonV3Tjeneste = get(),
            historikkvaskChannel = get(named("historikkvaskChannelK9Sak")),
        )
    }

    single {
        K9TilbakeTilLosAdapterTjeneste(
            behandlingProsessEventTilbakeRepository = BehandlingProsessEventTilbakeRepository(get()),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            pepCacheService = get(),
            oppgaveRepository = get(),
            reservasjonV3Tjeneste = get(),
            historikkvaskChannel = get(named("historikkvaskChannelK9Tilbake")),
        )
    }

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerKlientLocal()
    }

    single {
        K9KlageTilLosAdapterTjeneste(
            behandlingProsessEventKlageRepository = get(),
            områdeRepository = get(),
            feltdefinisjonTjeneste = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            transactionalManager = get(),
            config = get(),
            k9sakBeriker = get(),
            k9klageBeriker = get(),
        )
    }

    single<K9KlageBerikerInterfaceKludge> {
        K9KlageBerikerKlientLocal()
    }

    single {
        K9PunsjTilLosAdapterTjeneste(
            eventRepository = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            reservasjonV3Tjeneste = get(),
            config = config,
            transactionalManager = get(),
            pepCacheService = get(),
        )
    }

    single {
        ReservasjonV3Repository(
            transactionalManager = get(),
        )
    }

    single {
        ReservasjonV3Tjeneste(
            transactionalManager = get(),
            reservasjonV3Repository = get(),
            oppgaveV1Repository = get(),
            oppgaveV3Repository = get(),
            pepClient = get(),
            saksbehandlerRepository = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single {
        ReservasjonV3DtoBuilder(
            pdlService = get(),
            oppgaveTjeneste = get(),
            saksbehandlerRepository = get()
        )
    }

    single {
        OppgaveKoTjeneste(
            transactionalManager = get(),
            oppgaveKoRepository = get(),
            oppgaveQueryService = get(),
            oppgaveRepository = get(),
            aktivOppgaveRepository = get(),
            oppgaveRepositoryTxWrapper = get(),
            reservasjonV3Tjeneste = get(),
            saksbehandlerRepository = get(),
            oppgaveTjeneste = get(),
            reservasjonRepository = get(),
            pepClient = get(),
            pdlService = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
            feltdefinisjonTjeneste = get(),
        )
    }

    single {
        OppgaveQueryRepository(
            datasource = get(),
            feltdefinisjonRepository = get()
        )
    }

    single {
        OppgaveQueryService()
    }

    single {
        OppgaveKoRepository(
            datasource = get()
        )
    }

    single {
        OppgaveApisTjeneste(
            oppgaveTjeneste = get(),
            oppgaveV1Repository = get(),
            saksbehandlerRepository = get(),
            reservasjonV3Tjeneste = get(),
            oppgaveV3Repository = get(),
            transactionalManager = get(),
            reservasjonV3DtoBuilder = get(),
            reservasjonOversetter = get(),
        )
    }

    single {
        PepCacheRepository(dataSource = get())
    }

    single {
        OppgaveRepositoryTxWrapper(
            oppgaveRepository = get(),
            transactionalManager = get(),
        )
    }

    single<ForvaltningRepository> {
        ForvaltningRepository(
            oppgavetypeRepository = get(),
            transactionalManager = get(),
        )
    }

    single {
        RefreshK9v3Tjeneste(
            k9SakService = get(),
            oppgaveQueryService = get(),
            aktivOppgaveRepository = get(),
            oppgaveKoRepository = get(),
            transactionalManager = get()
        )
    }

    single {
        SøkeboksTjeneste(
            queryService = get(),
            oppgaveRepository = get(),
            pdlService = get(),
            pepClient = get(),
            reservasjonV3Tjeneste = get(),
            saksbehandlerRepository = get()
        )
    }

    single {
        StatusService(
            queryService = get(),
            oppgaverGruppertRepository = get(),
        )
    }

    single {
        DagensTallService(
            queryService = get(),
        )
    }

    single {
        FerdigstiltePerEnhetService(
            enheter = config.enheter(),
            queryService = get()
        )
    }
}
