package no.nav.k9.los

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.server.application.*
import kotlinx.coroutines.channels.Channel
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.k9.los.KoinProfile.*
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventmottak.FeilRekkefølgeSjekker
import no.nav.k9.los.domeneadaptere.eventmottak.k9.klage.K9KlageEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.k9.punsj.K9PunsjEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.k9.sak.K9SakEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.k9.tilbakekrav.K9TilbakeEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.kafka.KafkaConsumerLifecycleService
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventHandler
import no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake.UngTilbakeEventHandler
import no.nav.k9.los.domeneadaptere.eventtiloppgave.*
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.Områdesetup as AktOmrådesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.Områdesetup as K9Områdesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.klagetillos.beriker.K9KlageBerikerInterfaceKludge
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.klagetillos.beriker.K9KlageBerikerKlientLocal
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.klagetillos.beriker.K9KlageBerikerSystemKlient
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.beriker.K9SakSystemKlient
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.beriker.K9SakSystemKlientInterfaceKludge
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.beriker.K9SakSystemKlientLocal
import no.nav.k9.los.domeneadaptere.k9.avstemming.AvstemmingsTjeneste
import no.nav.k9.los.domeneadaptere.k9.avstemming.punsj.systemklient.LocalPunsjAvstemmingsklient
import no.nav.k9.los.domeneadaptere.k9.avstemming.punsj.systemklient.RestPunsjAvstemmingsklient
import no.nav.k9.los.domeneadaptere.k9.avstemming.saksbehandling.systemklient.LocalSakAvstemmingsklient
import no.nav.k9.los.domeneadaptere.k9.avstemming.saksbehandling.systemklient.RestSakAvstemmingsklient
import no.nav.k9.los.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9v3Tjeneste
import no.nav.k9.los.domeneadaptere.k9.refreshk9sakoppgaver.restklient.IK9SakService
import no.nav.k9.los.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakBehandlingOppfrisketRepository
import no.nav.k9.los.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakServiceLocal
import no.nav.k9.los.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakServiceSystemClient
import no.nav.k9.los.domeneadaptere.statistikk.*
import no.nav.k9.los.driftsmelding.DriftsmeldingRepository
import no.nav.k9.los.driftsmelding.DriftsmeldingTjeneste
import no.nav.k9.los.forvaltning.ForvaltningRepository
import no.nav.k9.los.infrastruktur.abac.*
import no.nav.k9.los.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.infrastruktur.azuregraph.AzureGraphService
import no.nav.k9.los.infrastruktur.azuregraph.AzureGraphServiceLocal
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.db.hikariConfig
import no.nav.k9.los.infrastruktur.metrikker.EventlagerNokkeltallPrometheusCollector
import no.nav.k9.los.infrastruktur.metrikker.EventlagerNokkeltallRepository
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.PdlService
import no.nav.k9.los.infrastruktur.pdl.PdlServiceLocal
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.innloggetbruker.InnloggetBrukerTjeneste
import no.nav.k9.los.ko.KøpåvirkendeHendelse
import no.nav.k9.los.ko.OppgaveKoTjeneste
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.lagretsok.LagretSøkRepository
import no.nav.k9.los.lagretsok.LagretSøkTjeneste
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.K9NyeOgFerdigstilteService
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.oppgavemottak.AktivOgPartisjonertOppgaveAjourholdTjeneste
import no.nav.k9.los.oppgavemottak.OppgaveV3Repository
import no.nav.k9.los.oppgavemottak.OppgaveV3Tjeneste
import no.nav.k9.los.oppgavemottak.PartisjonertOppgaveRepository
import no.nav.k9.los.oppgavemottak.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.oppgaveuthenting.OppgaveRepository
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.*
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.db.OppgaveQueryRepository
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDtoBuilder
import no.nav.k9.los.reservasjon.ReservasjonApisTjeneste
import no.nav.k9.los.reservasjon.ReservasjonV3DtoBuilder
import no.nav.k9.los.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerAdminTjeneste
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.sisteoppgaver.SisteOppgaverRepository
import no.nav.k9.los.sisteoppgaver.SisteOppgaverTjeneste
import no.nav.k9.los.søkeboks.K9SøkeboksTjeneste
import no.nav.k9.los.søkeboks.Oppgavesøkere
import no.nav.k9.los.søkeboks.SøkeboksTjeneste
import no.nav.k9.los.søkeboks.aktivitetspenger.AktivitetspengerOppgavesøk
import no.nav.k9.los.søkeboks.k9.K9Oppgavesøk
import no.nav.k9.los.uttrekk.UttrekkCsvGenerator
import no.nav.k9.los.uttrekk.UttrekkJobb
import no.nav.k9.los.uttrekk.UttrekkRepository
import no.nav.k9.los.uttrekk.UttrekkTjeneste
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.net.Proxy
import java.time.Clock
import java.util.*
import javax.sql.DataSource

fun selectModulesBasedOnProfile(application: Application, config: Configuration): List<Module> {
    return when (config.koinProfile()) {
        LOCAL -> listOf(common(application, config), localDevConfig())
        PREPROD -> listOf(common(application, config), naisCommonConfig(), preprodConfig(config))
        PROD -> listOf(common(application, config), naisCommonConfig(), prodConfig(config))
    }
}

fun common(app: Application, config: Configuration) = module {
    single { config.koinProfile() }
    single { config }
    single { RequestContextService(profile = get()) }
    single<DataSource> { hikariConfig(config) }

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

    single { TransactionalManager(dataSource = get()) }
    single<Clock> { Clock.systemDefaultZone() }

    single {
        SaksbehandlerRepository(
            dataSource = get(),
            transactionalManager = get(),
            områdeRepository = get(),
        )
    }

    single<IPepClient> { PepClient(get(), get()) }

    single { InnloggetBrukerTjeneste(get(), get(), get(), get()) }

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
            feilRekkefølgeSjekker = get(),
        )
    }

    single {
        K9KlageEventHandler(
            transactionalManager = get(),
            eventRepository = get(),
            oppgaveAdapter = get(),
            feilRekkefølgeSjekker = get(),
        )
    }

    single {
        K9TilbakeEventHandler(
            transactionalManager = get(),
            eventRepository = get(),
            oppgaveAdapter = get(),
            feilRekkefølgeSjekker = get(),
        )
    }

    single {
        K9PunsjEventHandler(
            transactionalManager = get(),
            oppgaveAdapter = get(),
            eventRepository = get(),
            feilRekkefølgeSjekker = get(),
        )
    }

    single {
        UngSakEventHandler(
            eventRepository = get(),
            transactionalManager = get(),
            feilRekkefølgeSjekker = get(),
        )
    }

    single {
        UngTilbakeEventHandler(
            eventRepository = get(),
            transactionalManager = get(),
            feilRekkefølgeSjekker = get(),
        )
    }

    single {
        EventRepository(
            dataSource = get(),
        )
    }

    single {
        EventlagerNokkeltallRepository(dataSource = get())
    }

    single {
        EventlagerNokkeltallPrometheusCollector(
            nokkeltallRepository = get(),
        )
    }

    single {
        KafkaConsumerLifecycleService(
            kafkaAivenConfig = config.getProfileAwareKafkaAivenConfig(),
            configuration = config,
            k9sakEventHandler = get(),
            k9TilbakeEventHandler = get(),
            k9PunsjEventHandler = get(),
            k9KlageEventHandler = get(),
            ungSakEventHandler = get(),
            ungTilbakeEventHandler = get(),
        )
    }

    single {
        SaksbehandlerAdminTjeneste(
            transactionalManager = get(),
            saksbehandlerRepository = get(),
            oppgaveKøV3Repository = get(),
            lagretSøkTjeneste = get(),
            uttrekkTjeneste = get(),
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
        HealthService(
            healthChecks = get<KafkaConsumerLifecycleService>().isHealtyChecks()
        )
    }

    single { FeltdefinisjonRepository(områdeRepository = get()) }
    single { OmrådeRepository(get()) }
    single {
        OppgavetypeRepository(
            dataSource = get(),
            feltdefinisjonRepository = get(),
            områdeRepository = get(),
            gyldigeFeltutledere = get()
        )
    }
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
            pepCacheRepository = get(),
        )
    }

    single {
        no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonTjeneste(
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
        no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeTjeneste(
            oppgavetypeRepository = get(),
            områdeRepository = get(),
            feltdefinisjonRepository = get(),
            transactionalManager = get(),
            gyldigeFeltutledere = get()
        )
    }

    single {
        K9Områdesetup(
            områdeRepository = get(),
            feltdefinisjonTjeneste = get(),
            oppgavetypeTjeneste = get(),
            config = get(),
        )
    }
    single {
        AktOmrådesetup(
            områdeRepository = get(),
            feltdefinisjonTjeneste = get(),
            oppgavetypeTjeneste = get(),
            frontendUrl = config.ungSakFrontendUrl(),
        )
    }

    single {
        EventTilOppgaveAdapter(
            eventRepository = get<EventRepository>(),
            oppgaveV3Tjeneste = get<OppgaveV3Tjeneste>(),
            transactionalManager = get<TransactionalManager>(),
            eventBeriker = get<EventBeriker>(),
            oppgaveOppdatertHandler = get<OppgaveOppdatertHandler>(),
            ajourholdTjeneste = get<AktivOgPartisjonertOppgaveAjourholdTjeneste>(),
            statistikkRepository = get<StatistikkRepository>(),
        )
    }

    single {
        AktivOgPartisjonertOppgaveAjourholdTjeneste(
            partisjonertOppgaveRepository = get(),
        )
    }


    single {
        FeilRekkefølgeSjekker()
    }

    single {
        EventBeriker(
            k9SakBeriker = get(),
            k9KlageBeriker = get(),
        )
    }

    single {
        OppgaveOppdatertHandler(
            oppgaveRepository = get(),
            reservasjonV3Tjeneste = get(),
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
        OppgaveQueryService(
            datasource = get(),
            oppgaveQueryRepository = get(),
            oppgaveRepository = get(),
            partisjonertOppgaveRepository = get(),
        )
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
            oppgaveSammendragDtoBuilder = get()
        )
    }

    single {
        OppgaveKoRepository(
            datasource = get(),
            områdeRepository = get()
        )
    }

    single {
        HistorikkvaskTjeneste(
            eventRepository = get(),
            oppgaveV3Tjeneste = get(),
            eventTilOppgaveAdapter = get(),
            transactionalManager = get()
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
            reservasjonsnøkkelOppgaveOppslag = get(),
            pepClient = get(),
            saksbehandlerRepository = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single<AktivOppgaveOppslag> {
        AktivOppgaveOppslagPartisjonert(
            oppgavetypeRepository = get(),
            transactionalManager = get(),
        )
    }
    single<ReservasjonsnøkkelOppgaveOppslag> {
        ReservasjonsnøkkelOppgaveOppslagPartisjonert(
            oppgavetypeRepository = get(),
            transactionalManager = get(),
        )
    }
    single<TemporalOppgaveOppslag> {
        TemporalOppgaveOppslagOppgaveV3(get(), get())
    }

    single {
        ReservasjonApisTjeneste(
            saksbehandlerRepository = get(),
            reservasjonV3Tjeneste = get(),
            transactionalManager = get(),
            reservasjonV3DtoBuilder = get(),
            aktivOppgaveOppslag = get(),
            pepClient = get(),
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
            oppgaveKoRepository = get(),
            transactionalManager = get()
        )
    }

    single {
        K9SøkeboksTjeneste(
            queryService = get(),
            pdlService = get(),
            pepClient = get(),
        )
    }

    single { Oppgavesøkere(K9Oppgavesøk(), AktivitetspengerOppgavesøk()) }
    single { OppgaveSammendragDtoBuilder(oppgavesøkere = get(), pdlService = get()) }
    single {
        SøkeboksTjeneste(
            pdlService = get(),
            pepClient = get(),
            oppgaveSammendragDtoBuilder = get(),
            queryService = get(),
            oppgavesøkere = get(),
        )
    }

    single {
        SisteOppgaverTjeneste(
            sisteOppgaverRepository = get(),
            oppgaveRepository = get(),
            pepClient = get(),
            pdlService = get(),
            transactionalManager = get(),
        )
    }

    single {
        no.nav.k9.los.nøkkeltall.avdelingsleder.status.K9StatusService(
            queryService = get(),
        )
    }

    single {
        no.nav.k9.los.nøkkeltall.avdelingsleder.dagenstall.K9DagensTallService(
            queryService = get(),
        )
    }

    single {
        no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.K9FerdigstiltePerEnhetService(
            queryService = get()
        )
    }

    single {
        K9NyeOgFerdigstilteService(
            queryService = get()
        )
    }

    single {
        no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.K9StatusFordelingService(
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
            lagretSøkRepository = get(),
            saksbehandlerRepository = get()
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

    single {
        SifAbacPdpKlient(
            sifAbacPdpKlientK9 = get<ISifAbacPdpKlient>(named("sifAbacPdpKlientK9")),
            sifAbacPdpKlientAktivitetspenger = get<ISifAbacPdpKlient>(named("sifAbacPdpKlientAktivitetspenger")),
        )
    }
}

// Kun lokalt, og verdikjede
fun localDevConfig() = module {
    single<IAzureGraphService> {
        AzureGraphServiceLocal()
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

    single<ISifAbacPdpKlient>(named("sifAbacPdpKlientK9")) {
        SifAbacPdpKlientLocal()
    }

    single<ISifAbacPdpKlient>(named("sifAbacPdpKlientAktivitetspenger")) {
        SifAbacPdpKlientLocal()
    }
}

// For både preprod og prod
fun naisCommonConfig() = module {
    single {
        // Standard httpclient uten proxy. Er eksplisitt på engine for å unngå en uforutsett engine fra classpath.
        // proxy må settes eksplisitt til DIRECT: lar vi den være null, arver java.net.http.HttpClient
        // ProxySelector.getDefault(), som plukker opp http.proxyHost fra webproxy-oppsettet i fss.
        HttpClient(Java) {
            engine {
                proxy = Proxy.NO_PROXY
            }
        }
    }

    single(named("sifAbacPdpHttpClient")) {
        HttpClient(Java) {
            engine {
                proxy = Proxy.NO_PROXY
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 1_000
                socketTimeoutMillis = 2_000
                requestTimeoutMillis = 2_000
            }
            HttpResponseValidator {
                handleResponseExceptionWithRequest { cause, _ ->
                    if (cause is HttpRequestTimeoutException ||
                        cause is ConnectTimeoutException ||
                        cause is SocketTimeoutException
                    ) {
                        throw SifAbacPdpUtilgjengeligException(cause)
                    }
                }
            }
        }
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

    single<ISifAbacPdpKlient>(named("sifAbacPdpKlientK9")) { SifAbacPdpKlientK9(
        configuration = get(),
        accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
        httpClient = get(named("sifAbacPdpHttpClient"))
    ) }

    single<ISifAbacPdpKlient>(named("sifAbacPdpKlientAktivitetspenger")) {
        SifAbacPdpKlientAktivitetspenger(
            configuration = get(),
            accessTokenClient = get<AccessTokenClientResolver>().azureV2(),
            httpClient = get(named("sifAbacPdpHttpClient"))
        )
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
