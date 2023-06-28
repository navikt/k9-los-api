package no.nav.k9.los

import io.ktor.server.application.Application
import kotlinx.coroutines.channels.Channel
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.k9.los.KoinProfile.LOCAL
import no.nav.k9.los.KoinProfile.PREPROD
import no.nav.k9.los.KoinProfile.PROD
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
import no.nav.k9.los.integrasjon.k9.K9SakService
import no.nav.k9.los.integrasjon.k9.K9SakServiceLocal
import no.nav.k9.los.integrasjon.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.integrasjon.omsorgspenger.IOmsorgspengerService
import no.nav.k9.los.integrasjon.omsorgspenger.OmsorgspengerService
import no.nav.k9.los.integrasjon.omsorgspenger.OmsorgspengerServiceLocal
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.pdl.PdlService
import no.nav.k9.los.integrasjon.pdl.PdlServiceLocal
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakBerikerKlient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9klagetillos.K9KlageTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.EventTilDtoMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosLukkeFeiloppgaverTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederTjeneste
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.NokkeltallTjeneste
import no.nav.k9.los.tjenester.driftsmeldinger.DriftsmeldingTjeneste
import no.nav.k9.los.tjenester.kodeverk.HentKodeverkTjeneste
import no.nav.k9.los.tjenester.saksbehandler.merknad.MerknadTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveKøOppdaterer
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
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
            statistikkRepository = get()
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
        no.nav.k9.los.domene.repository.StatistikkRepository(get())
    }

    single {
        SakOgBehandlingProducer(
            kafkaConfig = config.getKafkaConfig(),
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
            reservasjonTjeneste = get()
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
            azureGraphService = get()
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
            omsorgspengerService = get()
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
            reservasjonRepository = get(),
            oppgaveRepository = get(),
            pepClient = get(),
            configuration = config
        )
    }

    single {
        DriftsmeldingTjeneste(driftsmeldingRepository = get())
    }
    single {
        SakslisteTjeneste(oppgaveTjeneste = get())
    }
    single {
        HentKodeverkTjeneste()
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
    single { OppgavetypeRepository(feltdefinisjonRepository = get(), områdeRepository = get()) }
    single {
        OppgaveV3Repository(
            dataSource = get(),
            oppgavetypeRepository = get()
        )
    }
    single { K9SakOppgaveTilDVHMapper() }
    single { K9KlageOppgaveTilDVHMapper() }
    single { OppgaveRepository(oppgavetypeRepository = get()) }
    single { StatistikkRepository(dataSource = get()) }

    single {
        StatistikkPublisher(
            kafkaConfig = config.getProfileAwareKafkaAivenConfig(),
            config = config
        )
    }

    single {
        OppgavestatistikkTjeneste(
            oppgaveRepository = get(),
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
            transactionalManager = get()
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
        K9SakTilLosAdapterTjeneste(
            behandlingProsessEventK9Repository = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            oppgaveRepositoryV2 = get(),
            transactionalManager = get(),
            k9SakBerikerKlient = get(),
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
        K9KlageTilLosAdapterTjeneste(
            behandlingProsessEventKlageRepository = get(),
            områdeRepository = get(),
            feltdefinisjonTjeneste = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            transactionalManager = get(),
            config = get(),
        )
    }

    single {
        K9SakTilLosHistorikkvaskTjeneste(
            behandlingProsessEventK9Repository = get(),
            områdeRepository = get(),
            feltdefinisjonTjeneste = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            oppgaveRepositoryV2 = get(),
            k9SakTilLosAdapterTjeneste = get(),
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

    single<IOmsorgspengerService> {
        OmsorgspengerServiceLocal()
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
        K9SakService(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().naisSts()
        )
    }

    single<IOmsorgspengerService> {
        OmsorgspengerService(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2()
        )
    }

    single { RequestContextService(profile = PREPROD) }

    single<IPdlService> {
        PdlService(
            baseUrl = config.pdlUrl(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://dev-fss.pdl.pdl-api/.default"
        )
    }

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().naisSts()
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
        K9SakService(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().naisSts()
        )
    }

    single<IOmsorgspengerService> {
        OmsorgspengerService(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2()
        )
    }

    single { RequestContextService(profile = PROD) }

    single<IPdlService> {
        PdlService(
            baseUrl = config.pdlUrl(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://prod-fss.pdl.pdl-api/.default"
        )
    }

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().naisSts()
        )
    }

}

