package no.nav.k9

import io.ktor.application.Application
import kotlinx.coroutines.channels.Channel
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.k9.KoinProfile.LOCAL
import no.nav.k9.KoinProfile.PREPROD
import no.nav.k9.KoinProfile.PROD
import no.nav.k9.aksjonspunktbehandling.K9TilbakeEventHandler
import no.nav.k9.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.db.hikariConfig
import no.nav.k9.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.domene.repository.BehandlingProsessEventTilbakeRepository
import no.nav.k9.domene.repository.DriftsmeldingRepository
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.PunsjEventK9Repository
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.domene.repository.SaksbehandlerRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.fagsystem.k9sak.AksjonspunktHendelseMapper
import no.nav.k9.fagsystem.k9sak.K9sakEventHandlerV2
import no.nav.k9.integrasjon.abac.IPepClient
import no.nav.k9.integrasjon.abac.PepClient
import no.nav.k9.integrasjon.abac.PepClientLocal
import no.nav.k9.integrasjon.audit.Auditlogger
import no.nav.k9.integrasjon.azuregraph.AzureGraphService
import no.nav.k9.integrasjon.azuregraph.AzureGraphServiceLocal
import no.nav.k9.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.integrasjon.datavarehus.StatistikkProducer
import no.nav.k9.integrasjon.k9.IK9SakService
import no.nav.k9.integrasjon.k9.K9SakService
import no.nav.k9.integrasjon.k9.K9SakServiceLocal
import no.nav.k9.integrasjon.kafka.AsynkronProsesseringV1Service
import no.nav.k9.integrasjon.omsorgspenger.IOmsorgspengerService
import no.nav.k9.integrasjon.omsorgspenger.OmsorgspengerService
import no.nav.k9.integrasjon.omsorgspenger.OmsorgspengerServiceLocal
import no.nav.k9.integrasjon.pdl.IPdlService
import no.nav.k9.integrasjon.pdl.PdlService
import no.nav.k9.integrasjon.pdl.PdlServiceLocal
import no.nav.k9.integrasjon.rest.RequestContextService
import no.nav.k9.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.tjenester.avdelingsleder.AvdelingslederTjeneste
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.NokkeltallTjeneste
import no.nav.k9.tjenester.driftsmeldinger.DriftsmeldingTjeneste
import no.nav.k9.tjenester.kodeverk.HentKodeverkTjeneste
import no.nav.k9.tjenester.kokriterier.HentKøkritierierTjeneste
import no.nav.k9.tjenester.saksbehandler.merknad.MerknadTjeneste
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveKøOppdaterer
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import no.nav.k9.tjenester.saksbehandler.saksliste.SakslisteTjeneste
import no.nav.k9.tjenester.sse.RefreshKlienter.initializeRefreshKlienter
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

    single { OppgaveRepository(get(), get(), get(named("oppgaveRefreshChannel"))) }

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
        PunsjEventK9Repository(get())
    }

    single {
        BehandlingProsessEventTilbakeRepository(get())
    }

    single {
        StatistikkRepository(get())
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
            kafkaConfig = config.getKafkaConfig(),
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
            reservasjonTjeneste = get()
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
            punsjEventHandler = get()
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
    single {
        HentKøkritierierTjeneste()
    }

    single { OppgaveKøOppdaterer(get(), get(), get()) }

    single {
        MerknadTjeneste(
            oppgaveRepositoryV2 = get(),
            oppgaveKøOppdaterer = get(),
            azureGraphService = get(),
            migreringstjeneste = get(),
            tm = get()
        )
    }

    single {
        HealthService(
            healthChecks = get<AsynkronProsesseringV1Service>().isHealtyChecks()
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
}

