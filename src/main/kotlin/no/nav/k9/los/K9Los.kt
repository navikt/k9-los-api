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
import no.nav.k9.los.domeneadaptere.eventlager.EventlagerApi
import no.nav.k9.los.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventlagerApiNy
import no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.HistorikkvaskTjeneste
import no.nav.k9.los.domeneadaptere.k9.refreshk9sakoppgaver.K9sakBehandlingsoppfriskingJobb
import no.nav.k9.los.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9v3
import no.nav.k9.los.domeneadaptere.k9.statistikk.OppgavestatistikkTjeneste
import no.nav.k9.los.domeneadaptere.k9.statistikk.StatistikkApi
import no.nav.k9.los.domeneadaptere.k9.statistikk.StatistikkApiNy
import no.nav.k9.los.domeneadaptere.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.driftsmelding.DriftsmeldingerApis
import no.nav.k9.los.forvaltning.forvaltningApis
import no.nav.k9.los.forvaltning.forvaltningApisNy
import no.nav.k9.los.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.infrastruktur.db.DB_AWARE_PARALLELISM
import no.nav.k9.los.infrastruktur.db.migrate
import no.nav.k9.los.infrastruktur.jobbplanlegger.Jobbplanlegger
import no.nav.k9.los.infrastruktur.jobbplanlegger.PlanlagtJobb
import no.nav.k9.los.infrastruktur.jobbplanlegger.Tidsvindu
import no.nav.k9.los.infrastruktur.metrikker.EventlagerNokkeltallPrometheusCollector
import no.nav.k9.los.innloggetbruker.BrukersområderApi
import no.nav.k9.los.innloggetbruker.InnloggetBrukerApi
import no.nav.k9.los.ko.KøpåvirkendeHendelse
import no.nav.k9.los.ko.OppgaveKoApis
import no.nav.k9.los.ko.OppgaveKoAvdelingslederApisNy
import no.nav.k9.los.ko.OppgaveKoSaksbehandlerApisNy
import no.nav.k9.los.lagretsok.LagretSøkApi
import no.nav.k9.los.lagretsok.LagretSøkApiNy
import no.nav.k9.los.nøkkeltall.NøkkeltallV3Apis
import no.nav.k9.los.nøkkeltall.NøkkeltallV3ApisNy
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteApi
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteApiNy
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteService
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.FeltdefinisjonApi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.OppgavetypeApi
import no.nav.k9.los.oppgavemottak.OppgaveV3Api
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryApis
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryApisNy
import no.nav.k9.los.reservasjon.ManglerTilgangException
import no.nav.k9.los.reservasjon.ReservasjonAdminApisNy
import no.nav.k9.los.reservasjon.ReservasjonApis
import no.nav.k9.los.reservasjon.ReservasjonApisNy
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerAdminApis
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerAdminApisNy
import no.nav.k9.los.sisteoppgaver.SisteOppgaverApi
import no.nav.k9.los.sisteoppgaver.SisteOppgaverApiNy
import no.nav.k9.los.søkeboks.SøkeboksApi
import no.nav.k9.los.søkeboks.SøkeboksApiNy
import no.nav.k9.los.tjenester.mock.localSetup
import no.nav.k9.los.uttrekk.MigrerUttrekkResultatJobb
import no.nav.k9.los.uttrekk.UttrekkApi
import no.nav.k9.los.uttrekk.UttrekkApiNy
import no.nav.k9.los.uttrekk.UttrekkJobb
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

    koin.get<EventlagerNokkeltallPrometheusCollector>()

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
        exception<ManglerTilgangException> { call, _ ->
            call.respond(HttpStatusCode.Forbidden)
        }
    }

    // må se på om dette skal settes opp med Jobbplanlegger oppstartsjobb
    val refreshOppgaveV3Jobb = with(
        RefreshK9v3(
            refreshK9v3Tjeneste = koin.get()
        )
    ) { start(koin.get<Channel<KøpåvirkendeHendelse>>(named("KøpåvirkendeHendelseChannel"))) }


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
        pepCacheRepository = koin.get(),
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
    legacyApi()
    apiUnderConstruction()
}

private fun Route.legacyApi() {
    route("k9/los/api") {
        områdeApi(Områder.K9) {
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
    }
    route("api", { hidden = true }) {
        områdeApi(Områder.K9) {
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
                route("uttrekk") { UttrekkApi() }
            }
        }
    }
}

private fun Route.apiUnderConstruction() {
    route("driftsmeldinger", { tags("Driftsmelding") }) { DriftsmeldingerApis() }
    route("brukersområder") { BrukersområderApi() }
    områdeApi {
        route("innloggetbruker") { InnloggetBrukerApi() }
        swaggerUI("openapi.json")
        route("openapi.json") { openApi() }

        route("/forvaltning", { tags("Forvaltning") }) {
            route("eventlager") { EventlagerApiNy() }
            forvaltningApisNy()
            route("statistikk") { StatistikkApiNy() }
        }

        //TODO: tagge alle under her med "applikasjon"
        route("saksbehandler", { tags("Saksbehandler") }) {
            route("sok") { SøkeboksApiNy() }
            route("oppgaveko") { OppgaveKoSaksbehandlerApisNy() }
            route("reservasjoner") { ReservasjonApisNy() } //TODO: alle reservasjoner til egen fil under avdelingsleder
            route("siste-oppgaver") { SisteOppgaverApiNy() }
            route("nye-og-ferdigstilte") { NyeOgFerdigstilteApiNy() }
        }

        route("avdelingsleder", { tags("Avdelingsleder") }) {
            route("saksbehandler-admin") { SaksbehandlerAdminApisNy() }
            route("reservasjon-admin") { ReservasjonAdminApisNy() }
            route("oppgaveko") { OppgaveKoAvdelingslederApisNy() }
            route("nokkeltall") { NøkkeltallV3ApisNy() }
            route("lagret-sok") { LagretSøkApiNy() }
            route("uttrekk") { UttrekkApiNy() }
            route("query") { OppgaveQueryApisNy() }
        }
    }
}

fun Application.konfigurerJobber(koin: Koin, configuration: Configuration) {
    val historikkvaskTjeneste = koin.get<HistorikkvaskTjeneste>()
    val eventTilOppgaveAdapter = koin.get<EventTilOppgaveAdapter>()

    val oppgavestatistikkTjeneste = koin.get<OppgavestatistikkTjeneste>()

    val pepCacheService = koin.get<PepCacheService>()
    val statusFordelingService =
        koin.get<no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService>()
    val dagensTallService = koin.get<no.nav.k9.los.nøkkeltall.avdelingsleder.dagenstall.DagensTallService>()
    val perEnhetService =
        koin.get<no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetService>()
    val nyeOgFerdigstilteService = koin.get<NyeOgFerdigstilteService>()
    val uttrekkJobb = koin.get<UttrekkJobb>()
    val migrerUttrekkResultatJobb = MigrerUttrekkResultatJobb(koin.get())

    val k9sakBehandlingsoppfriskingJobb = K9sakBehandlingsoppfriskingJobb(
        reservasjonRepository = koin.get(),
        refreshK9v3Tjeneste = koin.get(),
        refreshOppgaveChannel = koin.get<Channel<UUID>>(named("oppgaveRefreshChannel")),
    )

    val høyPrioritet = 0
    val mediumPrioritet = 5
    val lavPrioritet = 10
    val utvidetArbeidstid = Tidsvindu.hverdager(5, 20)
    val heleTiden = Tidsvindu.alleDager()

    val planlagteJobber = buildSet {
        if (configuration.migreringEtterOppstart) {
            add(
                PlanlagtJobb.Oppstart(
                    navn = "FlywayMigrering",
                    prioritet = 0,
                ) {
                    migrate(configuration)
                }
            )
        }

        add(
            PlanlagtJobb.Oppstart(
                navn = "Setup",
                prioritet = 1,
            ) {
                koin.get<OmrådeSetup>().setup()
            })

        if (configuration.koinProfile == KoinProfile.LOCAL) {
            add(
                PlanlagtJobb.Oppstart(
                    navn = "Testdata",
                    prioritet = 1,
                ) {
                    localSetup.initSaksbehandlere()
                    localSetup.initPunsjoppgaver(0)
                    localSetup.initTilbakeoppgaver(0)
                    localSetup.initK9SakOppgaver(0)
                })
        }

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

        add(
            PlanlagtJobb.TimeJobb(
                navn = "K9sakBehandlingsoppfriskingJobb",
                prioritet = lavPrioritet,
                tidsvindu = Tidsvindu.hverdagerOgLørdag(5, 6),
                minutter = listOf(3), // vilkårlig valgt minutt tidlig i timen 5-6
            ) {
                k9sakBehandlingsoppfriskingJobb.utfør()
            }
        )

        add(
            PlanlagtJobb.Oppstart(
                navn = "MigrerUttrekkResultatFormat",
                prioritet = lavPrioritet,
            ) {
                migrerUttrekkResultatJobb.kjør()
            }
        )

        add(
            PlanlagtJobb.Periodisk(
                navn = "RyddOppUttrekkJobb",
                prioritet = lavPrioritet,
                tidsvindu = heleTiden,
                startForsinkelse = 0.seconds,
                intervall = 10.minutes
            ) {
                uttrekkJobb.ryddOppUttrekk()
            }
        )

        add(
            PlanlagtJobb.Periodisk(
                navn = "KjørUttrekkJobb",
                prioritet = lavPrioritet,
                tidsvindu = heleTiden,
                startForsinkelse = 10.seconds,
                intervall = 10.seconds
            ) {
                uttrekkJobb.kjørAlleUttrekkSomIkkeHarKjørt()
            }
        )

    }

    val jobbplanlegger = Jobbplanlegger(
        innkommendeJobber = planlagteJobber,
        coroutineContext = Dispatchers.IO.limitedParallelism(DB_AWARE_PARALLELISM),
    )

    monitor.subscribe(ApplicationStarted) {
        jobbplanlegger.start()
    }

    monitor.subscribe(ApplicationStopping) {
        jobbplanlegger.stopp()
    }
}
