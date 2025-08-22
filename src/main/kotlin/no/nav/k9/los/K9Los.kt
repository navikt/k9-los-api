package no.nav.k9.los

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import no.nav.helse.dusseldorf.ktor.auth.AuthStatusPages
import no.nav.helse.dusseldorf.ktor.auth.allIssuers
import no.nav.helse.dusseldorf.ktor.auth.multipleJwtIssuers
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthReporter
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.init
import no.nav.k9.los.eventhandler.køOppdatertProsessor
import no.nav.k9.los.eventhandler.oppdaterStatistikk
import no.nav.k9.los.eventhandler.sjekkReserverteJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.K9KlageTilLosApi
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.K9KlageTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosApi
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.K9TilbakeTilLosApi
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.K9TilbakeTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.k9TilbakeEksternId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.k9tilbakeKorrigerOutOfOrderProsessor
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.K9sakBehandlingsoppfriskingJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9v3
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.OppgavestatistikkTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkApi
import no.nav.k9.los.nyoppgavestyring.driftsmelding.DriftsmeldingerApis
import no.nav.k9.los.nyoppgavestyring.forvaltning.forvaltningApis
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger.Jobbplanlegger
import no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger.PlanlagtJobb
import no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger.Tidsvindu
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoApis
import no.nav.k9.los.nyoppgavestyring.kodeverk.KodeverkApis
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonApi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Api
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeApi
import no.nav.k9.los.nyoppgavestyring.nyeogferdigstilte.NyeOgFerdigstilteApi
import no.nav.k9.los.nyoppgavestyring.nyeogferdigstilte.NyeOgFerdigstilteService
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryApis
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonApis
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerAdminApis
import no.nav.k9.los.nyoppgavestyring.sisteoppgaver.SisteOppgaverApi
import no.nav.k9.los.nyoppgavestyring.søkeboks.SøkeboksApi
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.NøkkeltallV3Apis
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederApis
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.NokkeltallApis
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.AvdelingslederOppgavekøApis
import no.nav.k9.los.tjenester.fagsak.FagsakApis
import no.nav.k9.los.tjenester.konfig.KonfigApis
import no.nav.k9.los.tjenester.mock.localSetup
import no.nav.k9.los.tjenester.saksbehandler.NavAnsattApis
import no.nav.k9.los.tjenester.saksbehandler.nokkeltall.SaksbehandlerNøkkeltallApis
import no.nav.k9.los.tjenester.saksbehandler.saksliste.SaksbehandlerOppgavekoApis
import org.koin.core.Koin
import org.koin.core.qualifier.named
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.k9Los() {
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()

    val configuration = Configuration(environment.config)
    val issuers = configuration.issuers()

    install(Koin) {
        modules(selectModulesBasedOnProfile(this@k9Los, config = configuration))
    }

    val koin = getKoin()

    koin.get<OmrådeSetup>().setup()
    koin.get<K9SakTilLosAdapterTjeneste>().setup()
    koin.get<K9KlageTilLosAdapterTjeneste>().setup()
    koin.get<K9PunsjTilLosAdapterTjeneste>().setup()
    koin.get<K9TilbakeTilLosAdapterTjeneste>().setup()

    konfigurerJobber(koin, configuration)

    install(Authentication) {
        multipleJwtIssuers(issuers)
    }

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
        AuthStatusPages()
    }

    val køOppdatertProsessorJob =
        køOppdatertProsessor(
            oppgaveKøRepository = koin.get(),
            oppgaveRepository = koin.get(),
            oppgaveRepositoryV2 = koin.get(),
            channel = koin.get<Channel<UUID>>(named("oppgaveKøOppdatert")),
            refreshOppgaveChannel = koin.get<Channel<UUID>>(named("oppgaveRefreshChannel")),
            oppgaveTjeneste = koin.get()
        )

    // v1, skal fjernes
    val refreshOppgaveJobb = with(
        RefreshK9(
            k9SakService = koin.get(),
            oppgaveRepository = koin.get(),
            transactionalManager = koin.get()
        )
    ) { start(koin.get<Channel<UUID>>(named("oppgaveRefreshChannel"))) }

    // må se på om dette skal settes opp med Jobbplanlegger oppstartsjobb
    val refreshOppgaveV3Jobb = with(
        RefreshK9v3(
            refreshK9v3Tjeneste = koin.get()
        )
    ) { start(koin.get<Channel<KøpåvirkendeHendelse>>(named("KøpåvirkendeHendelseChannel"))) }

    // v1, skal fjernes
    val oppdaterStatistikkJobb =
        oppdaterStatistikk(
            channel = koin.get<Channel<Boolean>>(named("statistikkRefreshChannel")),
            configuration = configuration,
            statistikkRepository = koin.get(),
            oppgaveTjeneste = koin.get()
        )

    K9sakBehandlingsoppfriskingJobb(
        oppgaveRepository = koin.get(),
        oppgaveKøRepository = koin.get(),
        reservasjonRepository = koin.get(),
        refreshK9v3Tjeneste = koin.get(),
        refreshOppgaveChannel = koin.get<Channel<UUID>>(named("oppgaveRefreshChannel")),
        configuration = koin.get()
    ).run { start() }

    // v1, skal fjernes
    val sjekkReserverteJobb =
        sjekkReserverteJobb(saksbehandlerRepository = koin.get(), reservasjonRepository = koin.get())

    val asynkronProsesseringV1Service = koin.get<AsynkronProsesseringV1Service>()
    val sakOgBehadlingProducer = koin.get<SakOgBehandlingProducer>()

    val k9SakKorrigerOutOfOrderProsessor =
        k9SakKorrigerOutOfOrderProsessor(
            k9SakTilLosHistorikkvaskTjeneste = koin.get(),
            channel = koin.get<Channel<k9SakEksternId>>(named("historikkvaskChannelK9Sak")),
        )

    val k9TilbakeKorrigerOutOfOrderProsessor =
        k9tilbakeKorrigerOutOfOrderProsessor(
            k9TilbakeTilLosHistorikkvaskTjeneste = koin.get(),
            channel = koin.get<Channel<k9TilbakeEksternId>>(named("historikkvaskChannelK9Tilbake")),
        )

    monitor.subscribe(ApplicationStopping) {
        log.info("Stopper AsynkronProsesseringV1Service.")
        asynkronProsesseringV1Service.stop()
        sakOgBehadlingProducer.stop()
        sjekkReserverteJobb.cancel()
        log.info("AsynkronProsesseringV1Service Stoppet.")
        log.info("Stopper pipeline")
        køOppdatertProsessorJob.cancel()
        refreshOppgaveJobb.cancel()
        refreshOppgaveV3Jobb.cancel()
        oppdaterStatistikkJobb.cancel()
        k9SakKorrigerOutOfOrderProsessor.cancel()
        k9TilbakeKorrigerOutOfOrderProsessor.cancel()
    }

    // skal implementeres med Jobbplanlegger
    K9SakTilLosAdapterTjeneste(
        k9SakEventRepository = koin.get(),
        oppgavetypeTjeneste = koin.get(),
        oppgaveV3Tjeneste = koin.get(),
        config = koin.get(),
        transactionalManager = koin.get(),
        k9SakBerikerKlient = koin.get(),
        pepCacheService = koin.get(),
        oppgaveRepository = koin.get(),
        reservasjonV3Tjeneste = koin.get(),
        historikkvaskChannel = koin.get<Channel<k9SakEksternId>>(named("historikkvaskChannelK9Sak")),
        køpåvirkendeHendelseChannel = koin.get<Channel<KøpåvirkendeHendelse>>(named("KøpåvirkendeHendelseChannel")),
    ).kjør(kjørSetup = false, kjørUmiddelbart = false)

    // implementer med Jobbplanlegger
    K9KlageTilLosAdapterTjeneste(
        k9KlageEventRepository = koin.get(),
        områdeRepository = koin.get(),
        feltdefinisjonTjeneste = koin.get(),
        oppgavetypeTjeneste = koin.get(),
        oppgaveV3Tjeneste = koin.get(),
        transactionalManager = koin.get(),
        config = koin.get(),
        k9klageBeriker = koin.get(),
    ).kjør(kjørSetup = false, kjørUmiddelbart = false)

    // implementer med Jobbplanlegger
    K9PunsjTilLosAdapterTjeneste(
        k9PunsjEventRepository = koin.get(),
        oppgavetypeTjeneste = koin.get(),
        oppgaveV3Tjeneste = koin.get(),
        reservasjonV3Tjeneste = koin.get(),
        config = koin.get(),
        transactionalManager = koin.get(),
        pepCacheService = koin.get()
    ).kjør(kjørUmiddelbart = false)

    // implementer med Jobbplanlegger
    koin.get<K9TilbakeTilLosAdapterTjeneste>().kjør(kjørSetup = false, kjørUmiddelbart = false)

    // implementer med Jobbplanlegger
    OppgavestatistikkTjeneste(
        oppgavetypeRepository = koin.get(),
        statistikkPublisher = koin.get(),
        transactionalManager = koin.get(),
        statistikkRepository = koin.get(),
        pepClient = koin.get(),
        config = koin.get()
    ).kjør(kjørUmiddelbart = false)

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
    }

    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        init(appId)
        registry = prometheusMeterRegistry
    }
    routing {
        get("/metrics") {
            val metrics = CollectorRegistry.defaultRegistry.metricFamilySamples()
            call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                this.write("# Default registry starter her\n")
                TextFormat.write004(this, metrics)
                this.write("# Prometheus registry starter her\n")
                this.write(prometheusMeterRegistry.scrape())
            }
        }
        DefaultProbeRoutes()
        HealthRoute(healthService = koin.get())

        HealthReporter(
            app = appId,
            healthService = koin.get(),
            frequency = Duration.ofMinutes(1)
        )

        if ((KoinProfile.LOCAL == koin.get<KoinProfile>())) {
            localSetup.initSaksbehandlere()
            localSetup.initPunsjoppgaver(0)
            localSetup.initTilbakeoppgaver(0)
            localSetup.initK9SakOppgaver(0)
            api()
        } else {
            authenticate(*issuers.allIssuers()) {
                api()
            }
        }

        static("static") {
            resources("static/css")
            resources("static/js")
        }
    }


    intercept(ApplicationCallPipeline.Monitoring) {
        call.request.log()
    }

    install(CallId) {
        fromXCorrelationIdHeader()
    }

    install(OpenApi)
}

private fun Route.api() {
    route("k9/los/api") {
        route("openapi.json") {
            openApi()
        }
        swaggerUI("openapi.json")
        route("/forvaltning") {
            forvaltningApis()
            route("k9saktillos") { K9SakTilLosApi() }
            route("k9klagetillos") { K9KlageTilLosApi() }
            route("k9tilbaketillos") { K9TilbakeTilLosApi() }
            route("k9punsjtillos") { K9PunsjTilLosApi() }
            route("statistikk") { StatistikkApi() }
        }
    }
    route("api", {hidden = true}) {
        route("driftsmeldinger") {
            DriftsmeldingerApis()
        }
        route("fagsak") {
            FagsakApis() //Erstattet av søkeboksApi?
        }
        route("saksbehandler") {
            route("oppgaver") {
                ReservasjonApis()
            }

            SaksbehandlerOppgavekoApis()
            SaksbehandlerNøkkeltallApis()
        }
        route("avdelingsleder") {
            AvdelingslederApis()
            SaksbehandlerAdminApis()
            route("oppgavekoer") {
                AvdelingslederOppgavekøApis() // Erstattet av OppgaveKoApis i V3
            }
            route("nokkeltall") {
                NokkeltallApis()
            }
        }

        NavAnsattApis()

        route("konfig") { KonfigApis() }
        route("kodeverk") { KodeverkApis() }

        route("ny-oppgavestyring") {
            route("ko") { OppgaveKoApis() }
            route("oppgave") { OppgaveQueryApis() }
            route(
                "feltdefinisjon",
                {
                    hidden = true
                }) { FeltdefinisjonApi() } // Må legge til tilgangskontroll dersom disse endepunktene aktiveres
            route(
                "oppgavetype",
                {
                    hidden = true
                }) { OppgavetypeApi() } // Må legge til tilgangskontroll dersom disse endepunktene aktiveres
            route(
                "oppgave-v3",
                {
                    hidden = true
                }) { OppgaveV3Api() } // Må legge til tilgangskontroll dersom disse endepunktene aktiveres
            route("sok") { SøkeboksApi() }
            route("nokkeltall") { NøkkeltallV3Apis() }
            route("siste-oppgaver") { SisteOppgaverApi() }
            route("nye-og-ferdigstilte") { NyeOgFerdigstilteApi() }
        }
    }
}

fun Application.konfigurerJobber(koin: Koin, configuration: Configuration) {
    val k9SakTilLosHistorikkvaskTjeneste = koin.get<K9SakTilLosHistorikkvaskTjeneste>()
    val k9PunsjTilLosHistorikkvaskTjeneste = koin.get<K9PunsjTilLosHistorikkvaskTjeneste>()
    val k9TilbakeTilLosHistorikkvaskTjeneste = koin.get<K9TilbakeTilLosHistorikkvaskTjeneste>()
    val k9KlageTilLosHistorikkvaskTjeneste = koin.get<K9KlageTilLosHistorikkvaskTjeneste>()
    val pepCacheService = koin.get<PepCacheService>()
    val dagensTallService = koin.get<DagensTallService>()
    val perEnhetService = koin.get<FerdigstiltePerEnhetService>()
    val nyeOgFerdigstilteService = koin.get<NyeOgFerdigstilteService>()

    val høyPrioritet = 0
    val mediumPrioritet = 5
    val lavPrioritet = 10
    val utvidetArbeidstid = Tidsvindu.hverdager(5, 20)

    val planlagteJobber = buildSet {
        add(
            PlanlagtJobb.KjørPåTidspunkt(
                "K9SakTilLosHistorikkvask",
                høyPrioritet + 3,
                kjørTidligst = LocalDateTime.of(2025, 8, 11, 17, 0),
                kjørSenest = LocalDateTime.of(2025, 8, 11, 19, 30),
            ) {
                k9SakTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
        )

        add(
            PlanlagtJobb.KjørPåTidspunkt(
                "K9PunsjTilLosHistorikkvask",
                høyPrioritet + 2,
                kjørTidligst = LocalDateTime.of(2025, 8, 11, 17, 0),
                kjørSenest = LocalDateTime.of(2025, 8, 11, 19, 30),
            ) {
                k9PunsjTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
        )

        add(
            PlanlagtJobb.KjørPåTidspunkt(
                "K9TilbakeTilLosHistorikkvask",
                høyPrioritet + 1,
                kjørTidligst = LocalDateTime.of(2025, 8, 11, 17, 0),
                kjørSenest = LocalDateTime.of(2025, 8, 11, 19, 30),
            ) {
                k9TilbakeTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
        )

        add(
            PlanlagtJobb.KjørPåTidspunkt(
                "K9KlageTilLosHistorikkvask",
                høyPrioritet,
                kjørTidligst = LocalDateTime.of(2025, 8, 11, 17, 0),
                kjørSenest = LocalDateTime.of(2025, 8, 11, 19, 30),
            ) {
                k9KlageTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
        )

        // Hyppig oppdatering i arbeidstiden
        add(
            PlanlagtJobb.Periodisk(
                navn = "PepCacheOppdatererArbeidstid",
                prioritet = lavPrioritet,
                intervall = 5.seconds,
                tidsvindu = utvidetArbeidstid,
                startForsinkelse = 1.minutes
            ) {
                pepCacheService.oppdaterCacheForÅpneOgVentendeOppgaverEldreEnn()
            }
        )

        // Sjeldnere oppdatering utenfor arbeidstiden
        add(
            PlanlagtJobb.Periodisk(
                navn = "PepCacheOppdatererUtenforArbeidstid",
                prioritet = lavPrioritet,
                intervall = 30.seconds,
                tidsvindu = utvidetArbeidstid.komplement(),
                startForsinkelse = 1.minutes
            ) {
                pepCacheService.oppdaterCacheForÅpneOgVentendeOppgaverEldreEnn()
            }
        )

        add(
            PlanlagtJobb.Oppstart(
                navn = "DagensTallOppstart",
                prioritet = mediumPrioritet,
            ) {
                dagensTallService.oppdaterCache(this)
            }
        )

        add(
            PlanlagtJobb.Oppstart(
                navn = "PerEnhetOppstart",
                prioritet = mediumPrioritet,
            ) {
                perEnhetService.oppdaterCache(this)
            }
        )

        add(
            PlanlagtJobb.Oppstart(
                navn = "NyeOgFerdigstilteOppstart",
                prioritet = mediumPrioritet,
            ) {
                nyeOgFerdigstilteService.oppdaterCache(this)
            }
        )

        add(
            PlanlagtJobb.TimeJobb(
                navn = "DagensTallOppdaterer",
                prioritet = lavPrioritet,
                tidsvindu = Tidsvindu.alleDager(),
                minutter = listOf(0, 30),
            ) {
                dagensTallService.oppdaterCache(this)
            }
        )

        add(
            PlanlagtJobb.TimeJobb(
                navn = "PerEnhetOppdaterer",
                prioritet = lavPrioritet,
                tidsvindu = Tidsvindu.alleDager(),
                minutter = listOf(0, 30),
            ) {
                perEnhetService.oppdaterCache(this)
            }
        )

        add(
            PlanlagtJobb.Periodisk(
                navn = "NyeOgFerdigstilteOppdaterer",
                prioritet = lavPrioritet,
                startForsinkelse = 30.minutes,
                intervall = 30.minutes,
            ) {
                nyeOgFerdigstilteService.oppdaterCache(this)
            }
        )

    }

    val jobbplanlegger = Jobbplanlegger(
        innkommendeJobber = planlagteJobber,
        coroutineContext = Dispatchers.IO.limitedParallelism(4) + Span.current().asContextElement(),
    )

    monitor.subscribe(ApplicationStarted) {
        jobbplanlegger.start()
    }

    monitor.subscribe(ApplicationStopping) {
        jobbplanlegger.stopp()
    }
}