package no.nav.k9.los

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.server.application.*
import kotlinx.coroutines.channels.Channel
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.k9.los.KoinProfile.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.aktivvask.Aktivvask
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering.ReservasjonKonverteringJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.AvstemmingsTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.punsj.systemklient.LocalPunsjAvstemmingsklient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.punsj.systemklient.RestPunsjAvstemmingsklient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.systemklient.LocalSakAvstemmingsklient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.systemklient.RestSakAvstemmingsklient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.HistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.OppgaveOppdatertHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.VaskeeventSerieutleder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerSystemKlient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.PunsjEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.SakEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker.K9SakSystemKlient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker.K9SakSystemKlientInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker.K9SakSystemKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.TilbakeEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9v3Tjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.IK9SakService
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakBehandlingOppfrisketRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakServiceLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakServiceSystemClient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.*
import no.nav.k9.los.nyoppgavestyring.driftsmelding.DriftsmeldingRepository
import no.nav.k9.los.nyoppgavestyring.driftsmelding.DriftsmeldingTjeneste
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.forvaltning.ForvaltningRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.AzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.AzureGraphServiceLocal
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.hikariConfig
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PdlServiceLocal
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkRepository
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOgPartisjonertOppgaveAjourholdTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.PartisjonertOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.nyeogferdigstilte.NyeOgFerdigstilteService
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonApisTjeneste
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3DtoBuilder
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerAdminTjeneste
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.sisteoppgaver.SisteOppgaverRepository
import no.nav.k9.los.nyoppgavestyring.sisteoppgaver.SisteOppgaverTjeneste
import no.nav.k9.los.nyoppgavestyring.søkeboks.SøkeboksTjeneste
import no.nav.k9.los.nyoppgavestyring.uttrekk.UttrekkCsvGenerator
import no.nav.k9.los.nyoppgavestyring.uttrekk.UttrekkJobb
import no.nav.k9.los.nyoppgavestyring.uttrekk.UttrekkRepository
import no.nav.k9.los.nyoppgavestyring.uttrekk.UttrekkTjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.NøkkeltallRepositoryV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.status.StatusService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling.StatusFordelingService
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource

fun selectModulesBasedOnProfile(application: Application, config: Configuration): List<Module> {
    return when (config.koinProfile()) {
        LOCAL -> listOf(common(application, config), localDevConfig())
        PREPROD -> listOf(common(application, config), naisCommonConfig(config), preprodConfig(config))
        PROD -> listOf(common(application, config), naisCommonConfig(config), prodConfig(config))
    }
}

fun common(app: Application, config: Configuration) = module {
    single { config.koinProfile() }
    single { config }
    single { RequestContextService(profile = get()) }
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

    single { OppgaveRepository(get()) }

    single {
        AktivOppgaveRepository(
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
        GyldigeFeltutledere(
            saksbehandlerRepository = get()
        )
    }

    single {
        DriftsmeldingRepository(
            dataSource = get()
        )
    }

    single {
        NøkkeltallRepositoryV3(get())
    }

    single {
        OppgaverGruppertRepository(get())
    }

    single {
        StatistikkRepository(get(), get())
    }

    single {
        AccessTokenClientResolver(
            clients = config.clients()
        )
    }

    single {
        K9SakEventHandler(
            transactionalManager = get(),
            eventTilOppgaveAdapter = get(),
            eventRepository = get(),
        )
    }

    single {
        K9KlageEventHandler(
            transactionalManager = get(),
            eventRepository = get(),
            oppgaveAdapter = get(),
        )
    }

    single {
        K9TilbakeEventHandler(
            transactionalManager = get(),
            eventRepository = get(),
            oppgaveAdapter = get(),
        )
    }

    single {
        K9PunsjEventHandler(
            transactionalManager = get(),
            oppgaveAdapter = get(),
            eventRepository = get(),
        )
    }

    single {
        EventRepository(
            dataSource = get(),
        )
    }

    single {
        EventRepository(
            dataSource = get(),
        )
    }


    single {
        AsynkronProsesseringV1Service(
            kafkaAivenConfig = config.getProfileAwareKafkaAivenConfig(),
            configuration = config,
            k9sakEventHandler = get(),
            k9TilbakeEventHandler = get(),
            k9PunsjEventHandler = get(),
            k9KlageEventHandler = get(),
        )
    }

    single {
        ReservasjonOversetter(
            oppgaveV3RepositoryMedTxWrapper = get(),
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
        SaksbehandlerAdminTjeneste(
            pepClient = get(),
            transactionalManager = get(),
            saksbehandlerRepository = get(),
            oppgaveKøV3Repository = get(),
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
        HealthService(
            healthChecks = get<AsynkronProsesseringV1Service>().isHealtyChecks()
        )
    }

    single { FeltdefinisjonRepository(områdeRepository = get()) }
    single { OmrådeRepository(get()) }
    single { OppgavetypeRepository(dataSource = get(), feltdefinisjonRepository = get(), områdeRepository = get(), gyldigeFeltutledere = get()) }
    single {
        OppgaveV3Repository(
            dataSource = get(),
            oppgavetypeRepository = get()
        )
    }
    single {
        PartisjonertOppgaveRepository(
            oppgavetypeRepository = get()
        )
    }
    single { K9SakOppgaveTilDVHMapper() }
    single { K9KlageOppgaveTilDVHMapper() }
    single { OppgaveRepository(oppgavetypeRepository = get()) }

    single {
        SisteOppgaverRepository(dataSource = get())
    }

    single {
        StatistikkPublisher(
            kafkaConfig = config.getProfileAwareKafkaAivenConfig(),
            config = config
        )
    }

    single {
        OppgavestatistikkTjeneste(
            statistikkPublisher = get(),
            transactionalManager = get(),
            statistikkRepository = get(),
            pepClient = get(),
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
            transactionalManager = get(),
            gyldigeFeltutledere = get()
        )
    }

    single {
        OmrådeSetup(
            områdeRepository = get(),
            feltdefinisjonTjeneste = get(),
            oppgavetypeTjeneste = get(),
            config = get(),
        )
    }

    single {
        EventTilOppgaveAdapter(
            eventRepository = get(),
            oppgaveV3Tjeneste = get(),
            transactionalManager = get(),
            eventTilOppgaveMapper = get(),
            oppgaveOppdatertHandler = get(),
            vaskeeventSerieutleder = get(),
            ajourholdTjeneste = get()
        )
    }

    single {
        AktivOgPartisjonertOppgaveAjourholdTjeneste(
            partisjonertOppgaveRepository = get(),
        )
    }

    single {
        VaskeeventSerieutleder(
            sakEventTilOppgaveMapper = get(),
            klageEventTilOppgaveMapper = get(),
        )
    }

    single {
        EventTilOppgaveMapper(
            klageEventTilOppgaveMapper = get(),
            punsjEventTilOppgaveMapper = get(),
            sakEventTilOppgaveMapper = get(),
            tilbakeEventTilOppgaveMapper = get()
        )
    }

    single {
        SakEventTilOppgaveMapper(
            k9SakBerikerKlient = get(),
        )
    }

    single {
        KlageEventTilOppgaveMapper(
            k9klageBeriker = get()
        )
    }

    single {
        TilbakeEventTilOppgaveMapper()
    }

    single {
        PunsjEventTilOppgaveMapper()
    }

    single {
        OppgaveOppdatertHandler(
            oppgaveRepository = get(),
            reservasjonV3Tjeneste = get(),
            eventTilOppgaveMapper = get(),
            pepCacheService = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
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
        HistorikkvaskTjeneste(
            eventRepository = get(),
            oppgaveV3Tjeneste = get(),
            statistikkRepository = get(),
            eventTilOppgaveAdapter = get(),
            transactionalManager = get()
        )
    }

    single {
        Aktivvask(dataSource = get())
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
            oppgaveV3Repository = get(),
            pepClient = get(),
            saksbehandlerRepository = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single {
        OppgaveRepositoryTxWrapper(
            oppgaveRepository = get(),
            transactionalManager = get(),
        )
    }

    single {
        ReservasjonApisTjeneste(
            saksbehandlerRepository = get(),
            reservasjonV3Tjeneste = get(),
            oppgaveV3Repository = get(),
            transactionalManager = get(),
            reservasjonV3DtoBuilder = get(),
            reservasjonOversetter = get(),
            pepClient = get(),
            azureGraphService = get(),
        )
    }

    single {
        PepCacheRepository(dataSource = get())
    }

    single {
        K9SakBehandlingOppfrisketRepository(dataSource = get())
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
            saksbehandlerRepository = get(),
            pepClient = get(),
        )
    }

    single {
        SisteOppgaverTjeneste(
            oppgaveRepository = get(),
            pepClient = get(),
            sisteOppgaverRepository = get(),
            pdlService = get(),
            azureGraphService = get(),
            transactionalManager = get(),
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
            queryService = get()
        )
    }

    single {
        NyeOgFerdigstilteService(
            queryService = get()
        )
    }

    single {
        StatusFordelingService(
            queryService = get()
        )
    }

    single<LagretSøkRepository> {
        LagretSøkRepository(
            dataSource = get()
        )
    }

    single<LagretSøkTjeneste> {
        LagretSøkTjeneste(
            lagretSøkRepository = get(),
            saksbehandlerRepository = get(),
            oppgaveQueryService = get()
        )
    }

    single<UttrekkRepository> {
        UttrekkRepository(
            dataSource = get()
        )
    }

    single<UttrekkTjeneste> {
        UttrekkTjeneste(
            uttrekkRepository = get(),
            lagretSøkRepository = get()
        )
    }

    single<UttrekkJobb> {
        UttrekkJobb(
            oppgaveQueryService = get(),
            uttrekkTjeneste = get(),
        )
    }

    single<UttrekkCsvGenerator> {
        UttrekkCsvGenerator()
    }
}

// Kun lokalt, og verdikjede
fun localDevConfig() = module {
    single<IAzureGraphService> {
        AzureGraphServiceLocal()
    }
    single<IPepClient> {
        PepClientLocal()
    }

    single<IPdlService> {
        PdlServiceLocal()
    }
    single<IK9SakService> {
        K9SakServiceLocal()
    }

    single<K9SakSystemKlientInterfaceKludge> {
        K9SakSystemKlientLocal()
    }

    single<K9KlageBerikerInterfaceKludge> {
        K9KlageBerikerKlientLocal()
    }

    single<AvstemmingsTjeneste> {
        AvstemmingsTjeneste(
            oppgaveQueryService = get(),
            k9SakAvstemmingsklient = LocalSakAvstemmingsklient(),
            k9KlageAvstemmingsklient = LocalSakAvstemmingsklient(),
            k9PunsjAvstemmingsklient = LocalPunsjAvstemmingsklient()
        )
    }
}

// For både preprod og prod
fun naisCommonConfig(config: Configuration) = module {
    single {
        // Standard httpclient uten proxy. Er eksplisitt på engine for å unngå en uforutsett engine fra classpath.
        HttpClient(Java)
    }

    single(named("webproxyHttpClient")) {
        // Httpclient med webproxy, for trafikk ut på internett
        HttpClient(Java) {
            engine {
                proxy = ProxyBuilder.http("http://webproxy.nais:8088")
            }
        }
    }

    single<IAzureGraphService> {
        AzureGraphService(
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            httpClient = get(named("webproxyHttpClient"))
        )
    }

    single<IPepClient> {
        PepClient(azureGraphService = get(), get())
    }
}

// Unik konfigurasjon for preprod
fun preprodConfig(config: Configuration) = module {
    LoggerFactory.getLogger(KoinProfile::class.java).info("Koin preprodConfig loaded")
    single<IK9SakService> {
        K9SakServiceSystemClient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://dev-fss.k9saksbehandling.k9-sak/.default",
            k9SakBehandlingOppfrisketRepository = get(),
            httpClient = get()
        )
    }

    single<IPdlService> {
        PdlService(
            baseUrl = config.pdlUrl(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://dev-fss.pdl.pdl-api/.default",
            azureGraphService = get<IAzureGraphService>(),
            httpClient = get()
        )
    }

    single<K9SakSystemKlientInterfaceKludge> {
        K9SakSystemKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://dev-fss.k9saksbehandling.k9-sak/.default",
            httpClient = get()
        )
    }

    single<K9KlageBerikerInterfaceKludge> {
        K9KlageBerikerSystemKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scopeKlage = "api://dev-fss.k9saksbehandling.k9-klage/.default",
            scopeSak = "api://dev-fss.k9saksbehandling.k9-sak/.default",
            httpClient = get()
        )
    }

    single<ISifAbacPdpKlient> {
        SifAbacPdpKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://dev-fss.k9saksbehandling.sif-abac-pdp/.default",
            httpClient = get()
        )
    }

    single<AvstemmingsTjeneste> {
        AvstemmingsTjeneste(
            oppgaveQueryService = get(),
            k9SakAvstemmingsklient = RestSakAvstemmingsklient(
                url = config.k9Url(),
                navn = "k9sak",
                accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
                scope = "api://dev-fss.k9saksbehandling.k9-sak/.default",
                httpClient = get(),
            ),
            k9KlageAvstemmingsklient = RestSakAvstemmingsklient(
                url = config.k9KlageUrl(),
                navn = "k9klage",
                accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
                scope = "api://dev-fss.k9saksbehandling.k9-klage/.default",
                httpClient = get(),
            ),
            k9PunsjAvstemmingsklient = RestPunsjAvstemmingsklient(
                url = config.k9PunsjUrl(),
                navn = "k9punsj",
                accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
                scope = "api://dev-gcp.k9saksbehandling.k9-punsj/.default",
                httpClient = get(),
            )
        )
    }
}

// Unik konfigurasjon for prod
fun prodConfig(config: Configuration) = module {
    single<IK9SakService> {
        K9SakServiceSystemClient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://prod-fss.k9saksbehandling.k9-sak/.default",
            k9SakBehandlingOppfrisketRepository = get(),
            httpClient = get()
        )
    }

    single<IPdlService> {
        PdlService(
            baseUrl = config.pdlUrl(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://prod-fss.pdl.pdl-api/.default",
            azureGraphService = get<IAzureGraphService>(),
            httpClient = get()
        )
    }

    single<K9SakSystemKlientInterfaceKludge> {
        K9SakSystemKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://prod-fss.k9saksbehandling.k9-sak/.default",
            httpClient = get()
        )
    }

    single<K9KlageBerikerInterfaceKludge> {
        K9KlageBerikerSystemKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scopeKlage = "api://prod-fss.k9saksbehandling.k9-klage/.default",
            scopeSak = "api://prod-fss.k9saksbehandling.k9-sak/.default",
            httpClient = get()
        )
    }

    single<ISifAbacPdpKlient> {
        SifAbacPdpKlient(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            scope = "api://prod-fss.k9saksbehandling.sif-abac-pdp/.default",
            httpClient = get()
        )
    }

    single<AvstemmingsTjeneste> {
        AvstemmingsTjeneste(
            oppgaveQueryService = get(),
            k9SakAvstemmingsklient = RestSakAvstemmingsklient(
                url = config.k9Url(),
                navn = "k9sak",
                accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
                scope = "api://prod-fss.k9saksbehandling.k9-sak/.default",
                httpClient = get(),
            ),
            k9KlageAvstemmingsklient = RestSakAvstemmingsklient(
                url = config.k9KlageUrl(),
                navn = "k9klage",
                accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
                scope = "api://prod-fss.k9saksbehandling.k9-klage/.default",
                httpClient = get(),
            ),
            k9PunsjAvstemmingsklient = RestPunsjAvstemmingsklient(
                url = config.k9PunsjUrl(),
                navn = "k9punsj",
                accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
                scope = "api://prod-fss.k9saksbehandling.k9-punsj/.default",
                httpClient = get(),
            )
        )
    }
}
