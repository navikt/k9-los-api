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
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
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
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventlagerApi
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.HistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.K9sakBehandlingsoppfriskingJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9v3
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.OppgavestatistikkTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkApi
import no.nav.k9.los.nyoppgavestyring.driftsmelding.DriftsmeldingerApis
import no.nav.k9.los.nyoppgavestyring.forvaltning.forvaltningApis
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger.Jobbplanlegger
import no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger.PlanlagtJobb
import no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger.Tidsvindu
import no.nav.k9.los.nyoppgavestyring.innloggetbruker.InnloggetBrukerApi
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoApis
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkApi
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
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling.StatusFordelingService
import no.nav.k9.los.tjenester.mock.localSetup
import org.koin.core.Koin
import org.koin.core.qualifier.named
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>): Unit = EngineMain.main(args)

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

    // må se på om dette skal settes opp med Jobbplanlegger oppstartsjobb
    val refreshOppgaveV3Jobb = with(
        RefreshK9v3(
            refreshK9v3Tjeneste = koin.get()
        )
    ) { start(koin.get<Channel<KøpåvirkendeHendelse>>(named("KøpåvirkendeHendelseChannel"))) }

    K9sakBehandlingsoppfriskingJobb(
        reservasjonRepository = koin.get(),
        refreshK9v3Tjeneste = koin.get(),
        refreshOppgaveChannel = koin.get<Channel<UUID>>(named("oppgaveRefreshChannel")),
        configuration = koin.get()
    ).run { start() }

    val asynkronProsesseringV1Service = koin.get<AsynkronProsesseringV1Service>()

    monitor.subscribe(ApplicationStopping) {
        log.info("Stopper AsynkronProsesseringV1Service.")
        asynkronProsesseringV1Service.stop()
        log.info("AsynkronProsesseringV1Service Stoppet.")
        log.info("Stopper pipeline")
        refreshOppgaveV3Jobb.cancel()
    }

    OppgavestatistikkTjeneste(
        statistikkPublisher = koin.get(),
        transactionalManager = koin.get(),
        statistikkRepository = koin.get(),
        pepClient = koin.get(),
    )

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
            localSetup.initKlageoppgaver(0)
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
            route("eventlager") { EventlagerApi() }
            route("statistikk") { StatistikkApi() }
        }
    }
    route("api", { hidden = true }) {
        route("driftsmeldinger") {
            DriftsmeldingerApis()
        }
        route("saksbehandler") {
            route("oppgaver") {
                ReservasjonApis()
            }
        }
        route("avdelingsleder") {
            SaksbehandlerAdminApis()
        }

        InnloggetBrukerApi()

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
            route("lagret-sok") { LagretSøkApi() }
        }
    }
}

fun Application.konfigurerJobber(koin: Koin, configuration: Configuration) {
    val historikkvaskTjeneste = koin.get<HistorikkvaskTjeneste>()
    val eventTilOppgaveAdapter = koin.get<EventTilOppgaveAdapter>()

    val oppgavestatistikkTjeneste = koin.get<OppgavestatistikkTjeneste>()

    val pepCacheService = koin.get<PepCacheService>()
    val statusFordelingService = koin.get<StatusFordelingService>()
    val dagensTallService = koin.get<DagensTallService>()
    val perEnhetService = koin.get<FerdigstiltePerEnhetService>()
    val nyeOgFerdigstilteService = koin.get<NyeOgFerdigstilteService>()

    val høyPrioritet = 0
    val mediumPrioritet = 5
    val lavPrioritet = 10
    val utvidetArbeidstid = Tidsvindu.hverdager(5, 20)
    val heleTiden = Tidsvindu.alleDager()

    val planlagteJobber = buildSet {
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

        add(
            PlanlagtJobb.Periodisk(
                navn = "oppgaveVaktmester",
                prioritet = lavPrioritet,
                intervall = 1.minutes,
                tidsvindu = heleTiden,
                startForsinkelse = 1.minutes
            ) {
                eventTilOppgaveAdapter.spillAvBehandlingProsessEventer()
            }
        )

        add(
            PlanlagtJobb.Periodisk(
                navn = "HistorikkvaskVaktmester",
                prioritet = lavPrioritet,
                intervall = 1.minutes,
                tidsvindu = heleTiden,
                startForsinkelse = 1.minutes
            ) {
                historikkvaskTjeneste.kjørHistorikkvask()
            }
        )
/* //TODO: Midlertidig avskrudd ifbm vask og resending av fullt datasett til DVH
        add(
            PlanlagtJobb.Periodisk(
                navn = "Oppgavestatistikksender",
                prioritet = lavPrioritet,
                intervall = 1.minutes,
                tidsvindu = heleTiden,
                startForsinkelse = 1.minutes
            ) {
                oppgavestatistikkTjeneste.spillAvUsendtStatistikk()
            }
        )
*/
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
                navn = "StatusFordelingOppstart",
                prioritet = mediumPrioritet,
            ) {
                statusFordelingService.oppdaterCache(kode6 = false)
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
                navn = "StatusFordelingOppdaterer",
                prioritet = lavPrioritet,
                tidsvindu = utvidetArbeidstid,
                minutter = (0..55 step 5).toList(),
            ) {
                statusFordelingService.oppdaterCache(kode6 = false)
            }
        )

        add(
            PlanlagtJobb.TimeJobb(
                navn = "DagensTallOppdaterer",
                prioritet = lavPrioritet,
                tidsvindu = Tidsvindu.alleDager(),
                minutter = listOf(0, 10, 20, 30, 40, 50),
            ) {
                dagensTallService.oppdaterCache(this)
            }
        )

        add(
            PlanlagtJobb.TimeJobb(
                navn = "PerEnhetOppdaterer",
                prioritet = lavPrioritet,
                tidsvindu = Tidsvindu.alleDager(),
                minutter = listOf(0, 10, 20, 30, 40, 50),
            ) {
                perEnhetService.oppdaterCache(this)
            }
        )

        add(
            PlanlagtJobb.TimeJobb(
                navn = "NyeOgFerdigstilteOppdaterer",
                prioritet = lavPrioritet,
                tidsvindu = Tidsvindu.alleDager(),
                minutter = listOf(0, 10, 20, 30, 40, 50),
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