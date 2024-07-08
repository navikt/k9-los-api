package no.nav.k9.los

import io.ktor.server.application.*
import kotlinx.coroutines.channels.Channel
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.k9.los.KoinProfile.*
import no.nav.k9.los.aksjonspunktbehandling.K9KlageEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9TilbakeEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.db.hikariConfig
import no.nav.k9.los.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.fagsystem.k9sak.AksjonspunktHendelseMapper
import no.nav.k9.los.fagsystem.k9sak.K9sakEventHandlerV2
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.abac.PepClient
import no.nav.k9.los.integrasjon.abac.PepClientLocal
import no.nav.k9.los.integrasjon.audit.Auditlogger
import no.nav.k9.los.integrasjon.azuregraph.AzureGraphService
import no.nav.k9.los.integrasjon.azuregraph.AzureGraphServiceLocal
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.datavarehus.StatistikkProducer
import no.nav.k9.los.integrasjon.k9.IK9SakService
import no.nav.k9.los.integrasjon.k9.K9SakServiceLocal
import no.nav.k9.los.integrasjon.k9.K9SakServiceSystemClient
import no.nav.k9.los.integrasjon.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.pdl.PdlService
import no.nav.k9.los.integrasjon.pdl.PdlServiceLocal
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerSystemKlient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonKonverteringJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.k9SakEksternId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9klagetillos.K9KlageTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosAktivvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosLukkeFeiloppgaverTjeneste
import no.nav.k9.los.nyoppgavestyring.forvaltning.ForvaltningRepository
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheService
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederTjeneste
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.NokkeltallTjeneste
import no.nav.k9.los.tjenester.driftsmeldinger.DriftsmeldingTjeneste
import no.nav.k9.los.tjenester.kodeverk.HentKodeverkTjeneste
import no.nav.k9.los.tjenester.saksbehandler.merknad.MerknadTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.*
import no.nav.k9.los.tjenester.saksbehandler.saksliste.SakslisteTjeneste
import no.nav.k9.los.tjenester.sse.RefreshKlienter.initializeRefreshKlienter
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.*
import javax.sql.DataSource

fun selectModuleBasedOnProfile(application: Application, config: Configuration): List<Module> {
    val envModule = when (config.koinProfile()) {
        LOCAL -> localDevConfig()
        PREPROD -> preprodConfig(config)
        PROD -> prodConfig(config)
    }
    return listOf(common(application, config), envModule)
}

fun common(app: Application, config: Configuration) = module {
    single { config.koinProfile() }
    single { config }
    single<DataSource> { app.hikariConfig(config) }
    single {
        NokkeltallTjeneste(
            oppgaveRepository = get(),
            statistikkRepository = get(),
            nøkkeltallRepository = get(),
        )
    }
    single(named("oppgaveKøOppdatert")) {
        Channel<UUID>(Channel.UNLIMITED)
    }
    single(named("refreshKlienter")) {
        initializeRefreshKlienter()
    }
    single(named("oppgaveRefreshChannel")) {
        Channel<UUID>(Channel.UNLIMITED)
    }
    single(named("statistikkRefreshChannel")) {
        Channel<Boolean>(Channel.CONFLATED)
    }
    single(named("historikkvaskChannelK9Sak")) {
        Channel<k9SakEksternId>(Channel.UNLIMITED)
    }

    single { no.nav.k9.los.domene.repository.OppgaveRepository(get(), get(), get(named("oppgaveRefreshChannel"))) }

    single {
        OppgaveKøRepository(
            dataSource = get(),
            oppgaveRepositoryV2 = get(),
            oppgaveKøOppdatert = get(named("oppgaveKøOppdatert")),
            refreshKlienter = get(named("refreshKlienter")),
            oppgaveRefreshChannel = get(named("oppgaveRefreshChannel")),
            pepClient = get()
        )
    }

    single { AksjonspunktHendelseMapper(get()) }
    single { OppgaveRepositoryV2(dataSource = get()) }
    single { TransactionalManager(dataSource = get()) }
    single { BehandlingsmigreringTjeneste(get()) }
    single { OppgaveTjenesteV2(get(), get(), get()) }

    single {
        SaksbehandlerRepository(
            dataSource = get(),
            pepClient = get()
        )
    }

    single {
        DriftsmeldingRepository(
            dataSource = get()
        )
    }

    single {
        ReservasjonRepository(
            oppgaveRepository = get(),
            oppgaveRepositoryV2 = get(),
            oppgaveKøRepository = get(),
            dataSource = get(),
            refreshKlienter = get(named("refreshKlienter")),
            saksbehandlerRepository = get()
        )
    }

    single {
        BehandlingProsessEventK9Repository(get())
    }

    single {
        BehandlingProsessEventKlageRepository(get())
    }

    single {
        PunsjEventK9Repository(get())
    }

    single {
        BehandlingProsessEventTilbakeRepository(get())
    }

    single {
        NøkkeltallRepository(get())
    }

    single {
        no.nav.k9.los.domene.repository.StatistikkRepository(get())
    }

    single {
        SakOgBehandlingProducer(
            kafkaConfig = config.getProfileAwareKafkaAivenConfig(),
            config = config
        )
    }

    single {
        AccessTokenClientResolver(
            clients = config.clients()
        )
    }

    single {
        StatistikkProducer(
            kafkaConfig = config.getProfileAwareKafkaAivenConfig(),
            config = config,
            pepClient = get(),
            saksbehandlerRepository = get(),
            reservasjonRepository = get()
        )
    }

    single {
        K9sakEventHandlerV2(
            oppgaveTjenesteV2 = get(),
            aksjonspunktHendelseMapper = get()
        )
    }

    single {
        K9sakEventHandler(
            oppgaveRepository = get(),
            behandlingProsessEventK9Repository = get(),
            sakOgBehandlingProducer = get(),
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            statistikkProducer = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            statistikkRepository = get(),
            reservasjonTjeneste = get(),
            k9SakTilLosAdapterTjeneste = get(),
        )
    }

    single {
        K9KlageEventHandler(
            behandlingProsessEventKlageRepository = get(),
            k9KlageTilLosAdapterTjeneste = get(),
        )
    }

    single {
        K9TilbakeEventHandler(
            oppgaveRepository = get(),
            behandlingProsessEventTilbakeRepository = get(),
            sakOgBehandlingProducer = get(),
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            statistikkRepository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            reservasjonTjeneste = get(),
            reservasjonV3Tjeneste = get(),
            reservasjonOversetter = get(),
            saksbehandlerRepository = get(),
        )
    }

    single {
        K9punsjEventHandler(
            oppgaveRepository = get(),
            oppgaveTjenesteV2 = get(),
            punsjEventK9Repository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            reservasjonTjeneste = get(),
            statistikkRepository = get(),
            azureGraphService = get(),
            punsjTilLosAdapterTjeneste = get()
        )
    }


    single {
        AsynkronProsesseringV1Service(
            kafkaConfig = config.getKafkaConfig(),
            kafkaAivenConfig = config.getProfileAwareKafkaAivenConfig(),
            configuration = config,
            k9sakEventHandler = get(),
            k9sakEventHandlerv2 = get(),
            k9TilbakeEventHandler = get(),
            punsjEventHandler = get(),
            k9KlageEventHandler = get(),
        )
    }

    single {
        OppgaveTjeneste(
            oppgaveRepository = get(),
            oppgaveRepositoryV2 = get(),
            oppgaveKøRepository = get(),
            saksbehandlerRepository = get(),
            reservasjonRepository = get(),
            pdlService = get(),
            configuration = config,
            pepClient = get(),
            azureGraphService = get(),
            statistikkRepository = get(),
            reservasjonOversetter = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
        )
    }

    single {
        ReservasjonOversetter(
            transactionalManager = get(),
            oppgaveV3Repository = get(),
            saksbehandlerRepository = get(),
            reservasjonV3Tjeneste = get(),
            oppgaveV1Repository = get(),
            oppgaveV3RepositoryMedTxWrapper = get(),
        )
    }

    single {
        ReservasjonKonverteringJobb(
            config = get(),
            reservasjonRepository = get(),
            oppgaveRepository = get(),
            reservasjonOversetter = get(),
            saksbehandlerRepository = get(),
        )
    }

    single {
        ReservasjonTjeneste(
            reservasjonRepository = get(),
            saksbehandlerRepository = get()
        )
    }

    single {
        AvdelingslederTjeneste(
            oppgaveKøRepository = get(),
            saksbehandlerRepository = get(),
            oppgaveTjeneste = get(),
            pepClient = get(),
            reservasjonV3Tjeneste = get(),
            reservasjonV3DtoBuilder = get(),
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
        DriftsmeldingTjeneste(driftsmeldingRepository = get())
    }
    single {
        SakslisteTjeneste(oppgaveTjeneste = get())
    }
    single {
        HentKodeverkTjeneste(configuration = get())
    }

    single { OppgaveKøOppdaterer(get(), get(), get()) }

    single {
        MerknadTjeneste(
            oppgaveRepositoryV2 = get(),
            oppgaveKøOppdaterer = get(),
            azureGraphService = get(),
            migreringstjeneste = get(),
            k9SakTilLosAdapterTjeneste = get(),
            behandlingProsessEventK9Repository = get(),
            tm = get()
        )
    }

    single {
        HealthService(
            healthChecks = get<AsynkronProsesseringV1Service>().isHealtyChecks()
        )
    }

    single { FeltdefinisjonRepository(områdeRepository = get()) }
    single { OmrådeRepository(get()) }
    single { OppgavetypeRepository(dataSource = get(), feltdefinisjonRepository = get(), områdeRepository = get()) }
    single {
        OppgaveV3Repository(
            dataSource = get(),
            oppgavetypeRepository = get()
        )
    }
    single { K9SakOppgaveTilDVHMapper() }
    single { K9KlageOppgaveTilDVHMapper() }
    single { OppgaveRepository(oppgavetypeRepository = get()) }
    single {
        StatistikkRepository(
            dataSource = get(),
            oppgavetypeRepository = get(),
        )
    }

    single {
        StatistikkPublisher(
            kafkaConfig = config.getProfileAwareKafkaAivenConfig(),
            config = config
        )
    }

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
            oppgavetypeRepository = get(),
            områdeRepository = get(),
        )
    }
    single {
        OppgavetypeTjeneste(
            oppgavetypeRepository = get(),
            områdeRepository = get(),
            feltdefinisjonRepository = get(),
            transactionalManager = get()
        )
    }

    single {
        OmrådeSetup(
            områdeRepository = get(),
            feltdefinisjonTjeneste = get()
        )
    }

    single {
        K9PunsjTilLosAdapterTjeneste(
            eventRepository = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            reservasjonV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            pepCacheService = get()
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
            historikkvaskChannel = get(named("historikkvaskChannelK9Sak"))
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
        OppgaveKoTjeneste(
            transactionalManager = get(),
            oppgaveKoRepository = get(),
            oppgaveQueryService = get(),
            oppgaveRepository = get(),
            reservasjonV3Tjeneste = get(),
            saksbehandlerRepository = get(),
            oppgaveTjeneste = get(),
            reservasjonRepository = get(),
            oppgaveRepositoryTxWrapper = get(),
            pepClient = get(),
            pdlService = get(),
            reservasjonV3Repository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel"))
        )
    }

    single {
        OppgaveKoRepository(
            datasource = get()
        )
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
        )
    }

    single {
        K9SakTilLosHistorikkvaskTjeneste(
            behandlingProsessEventK9Repository = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            k9SakTilLosAdapterTjeneste = get(),
            k9SakBerikerKlient = get()
        )
    }

    single {
        K9SakTilLosAktivvaskTjeneste(
            behandlingProsessEventK9Repository = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            k9SakTilLosAdapterTjeneste = get(),
            k9SakBerikerKlient = get()
        )
    }

    single {
        K9KlageTilLosHistorikkvaskTjeneste(
            behandlingProsessEventKlageRepository = get(),
            områdeRepository = get(),
            feltdefinisjonTjeneste = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            k9sakBeriker = get(),
        )
    }

    single {
        K9SakTilLosLukkeFeiloppgaverTjeneste(
            behandlingProsessEventK9Repository = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            k9SakBerikerKlient = get(),
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
            auditlogger = Auditlogger(config),
        )
    }

    single {
        no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper(
            oppgaveRepository = get(),
            transactionalManager = get(),
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
        PepCacheService(
            pepClient = get(),
            pepCacheRepository = get(),
            oppgaveRepository = get(),
            transactionalManager = get()
        )
    }

    single<ForvaltningRepository> {
        ForvaltningRepository(
            oppgavetypeRepository = get(),
            transactionalManager = get(),
        )
    }
}

fun localDevConfig() = module {
    single<IAzureGraphService> {
        AzureGraphServiceLocal()
    }
    single<IPepClient> {
        PepClientLocal()
    }
    single { RequestContextService(profile = LOCAL) }

    single<IPdlService> {
        PdlServiceLocal()
    }
    single<IK9SakService> {
        K9SakServiceLocal()
    }

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerKlientLocal()
    }

}

fun preprodConfig(config: Configuration) = module {
    single<IAzureGraphService> {
        AzureGraphService(
            accessTokenClient = get<AccessTokenClientResolver>().azureV2()
        )
    }
    single<IPepClient> {
        PepClient(azureGraphService = get(), auditlogger = Auditlogger(config), config = config)
    }
    single<IK9SakService> {
        K9SakServiceSystemClient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://dev-fss.k9saksbehandling.k9-sak/.default"
        )
    }

    single { RequestContextService(profile = PREPROD) }

    single<IPdlService> {
        PdlService(
            baseUrl = config.pdlUrl(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://dev-fss.pdl.pdl-api/.default",
            azureGraphService = get<IAzureGraphService>()
        )
    }

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerSystemKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://dev-fss.k9saksbehandling.k9-sak/.default"
        )
    }
}

fun prodConfig(config: Configuration) = module {
    single<IAzureGraphService> {
        AzureGraphService(
            accessTokenClient = get<AccessTokenClientResolver>().azureV2()
        )
    }
    single<IPepClient> {
        PepClient(azureGraphService = get(), auditlogger = Auditlogger(config), config = config)
    }
    single<IK9SakService> {
        K9SakServiceSystemClient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://prod-fss.k9saksbehandling.k9-sak/.default"
        )
    }

    single { RequestContextService(profile = PROD) }

    single<IPdlService> {
        PdlService(
            baseUrl = config.pdlUrl(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://prod-fss.pdl.pdl-api/.default",
            azureGraphService = get<IAzureGraphService>()
        )
    }

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerSystemKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://prod-fss.k9saksbehandling.k9-sak/.default"
        )
    }
}

