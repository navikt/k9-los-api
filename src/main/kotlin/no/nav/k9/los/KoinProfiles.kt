package no.nav.k9.los

import io.ktor.server.application.*
import kotlinx.coroutines.channels.Channel
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.k9.los.KoinProfile.*
import no.nav.k9.los.aksjonspunktbehandling.K9KlageEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9TilbakeEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.auditlogger.K9Auditlogger
import no.nav.k9.los.db.hikariConfig
import no.nav.k9.los.db.util.TransactionalManager
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.registerrefreshk9sak.RefreshK9v3Tjeneste
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.abac.PepClient
import no.nav.k9.los.integrasjon.abac.PepClientLocal
import no.nav.k9.los.integrasjon.audit.Auditlogger
import no.nav.k9.los.integrasjon.azuregraph.AzureGraphService
import no.nav.k9.los.integrasjon.azuregraph.AzureGraphServiceLocal
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.k9.IK9SakService
import no.nav.k9.los.integrasjon.k9.K9SakBehandlingOppfrisketRepostiory
import no.nav.k9.los.integrasjon.k9.K9SakServiceLocal
import no.nav.k9.los.integrasjon.k9.K9SakServiceSystemClient
import no.nav.k9.los.integrasjon.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.pdl.PdlService
import no.nav.k9.los.integrasjon.pdl.PdlServiceLocal
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.aktivvask.Aktivvask
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerSystemKlient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.K9KlageTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.punsjtillos.K9PunsjTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.K9SakTilLosLukkeFeiloppgaverTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.k9SakEksternId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.K9TilbakeTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.k9TilbakeEksternId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonKonverteringJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.forvaltning.ForvaltningRepository
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltVerdiUtledere
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
import no.nav.k9.los.nyoppgavestyring.søkeboks.SøkeboksTjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.NøkkeltallRepositoryV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.status.StatusService
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederTjeneste
import no.nav.k9.los.tjenester.driftsmeldinger.DriftsmeldingTjeneste
import no.nav.k9.los.tjenester.kodeverk.HentKodeverkTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.*
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

    single(named("oppgaveKøOppdatert")) {
        Channel<UUID>(Channel.UNLIMITED)
    }
    single(named("oppgaveRefreshChannel")) {
        Channel<UUID>(Channel.UNLIMITED)
    }
    single(named("KøpåvirkendeHendelseChannel")) {
        Channel<KøpåvirkendeHendelse>(Channel.UNLIMITED)
    }
    single(named("statistikkRefreshChannel")) {
        Channel<Boolean>(Channel.CONFLATED)
    }
    single(named("historikkvaskChannelK9Sak")) {
        Channel<k9SakEksternId>(Channel.UNLIMITED)
    }
    single(named("historikkvaskChannelK9Tilbake")) {
        Channel<k9TilbakeEksternId>(Channel.UNLIMITED)
    }

    single { AktivOppgaveRepository(
        oppgavetypeRepository = get()
    )
    }

    single { TransactionalManager(dataSource = get()) }

    single {
        SaksbehandlerRepository(
            dataSource = get(),
            pepClient = get()
        )
    }

    single {
        OppgaveFeltVerdiUtledere(
            saksbehandlerRepository = get()
        )
    }

    single {
        DriftsmeldingRepository(
            dataSource = get()
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
        NøkkeltallRepositoryV3(get())
    }

    single {
        OppgaverGruppertRepository(get())
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
        K9sakEventHandler(
            behandlingProsessEventK9Repository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            k9SakTilLosAdapterTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single {
        K9KlageEventHandler(
            behandlingProsessEventKlageRepository = get(),
            k9KlageTilLosAdapterTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single {
        K9TilbakeEventHandler(
            behandlingProsessEventTilbakeRepository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
            k9TilbakeTilLosAdapterTjeneste = get(),
        )
    }

    single {
        K9punsjEventHandler(
            punsjEventK9Repository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            punsjTilLosAdapterTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    //Starter eventlistener-streams?
    single {
        AsynkronProsesseringV1Service(
            kafkaConfig = config.getKafkaConfig(),
            kafkaAivenConfig = config.getProfileAwareKafkaAivenConfig(),
            configuration = config,
            k9sakEventHandler = get(),
            k9TilbakeEventHandler = get(),
            punsjEventHandler = get(),
            k9KlageEventHandler = get(),
        )
    }

    single {
        ReservasjonKonverteringJobb(
            config = get(),
            reservasjonV3Tjeneste = get(),
            transactionalManager = get(),
            oppgaveRepository = get(),
        )
    }

    single {
        AvdelingslederTjeneste(
            transactionalManager = get(),
            oppgaveKøV3Repository = get(),
            saksbehandlerRepository = get(),
            pepClient = get(),
            reservasjonV3Tjeneste = get(),
        )
    }

    single {
        ReservasjonV3DtoBuilder(
            pdlService = get(),
            saksbehandlerRepository = get()
        )
    }

    single {
        DriftsmeldingTjeneste(driftsmeldingRepository = get())
    }

    single {
        HentKodeverkTjeneste()
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
            pepCacheService = get(),
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
            behandlingProsessEventTilbakeRepository = get(),
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
            reservasjonV3Tjeneste = get(),
            saksbehandlerRepository = get(),
            pepClient = get(),
            pdlService = get(),
            aktivOppgaveRepository = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
            feltdefinisjonTjeneste = get(),
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
            k9SakBerikerKlient = get(),
        )
    }

    single {
        K9PunsjTilLosHistorikkvaskTjeneste(
            eventRepository = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
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
        K9TilbakeTilLosHistorikkvaskTjeneste(
            behandlingProsessEventTilbakeRepository = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
        )
    }

    single {
        Aktivvask(dataSource = get())
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
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
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
            saksbehandlerRepository = get(),
            reservasjonV3Tjeneste = get(),
            oppgaveV3Repository = get(),
            transactionalManager = get(),
            reservasjonV3DtoBuilder = get(),
        )
    }

    single {
        PepCacheRepository(dataSource = get())
    }

    single {
        K9SakBehandlingOppfrisketRepostiory(dataSource = get())
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
        PepClient(azureGraphService = get(), config = config, k9Auditlogger = K9Auditlogger(Auditlogger(config)))
    }
    single<IK9SakService> {
        K9SakServiceSystemClient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://dev-fss.k9saksbehandling.k9-sak/.default",
            k9SakBehandlingOppfrisketRepostiory = get()
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
        PepClient(azureGraphService = get(), config = config, k9Auditlogger = K9Auditlogger(Auditlogger(config)))
    }
    single<IK9SakService> {
        K9SakServiceSystemClient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://prod-fss.k9saksbehandling.k9-sak/.default",
            k9SakBehandlingOppfrisketRepostiory = get()
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

