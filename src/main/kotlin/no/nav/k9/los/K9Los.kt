@file:OptIn(ExperimentalCoroutinesApi::class)

package no.nav.k9.los

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import no.nav.helse.dusseldorf.ktor.auth.AuthStatusPages
import no.nav.helse.dusseldorf.ktor.auth.allIssuers
import no.nav.helse.dusseldorf.ktor.auth.multipleJwtIssuers
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthReporter
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.dusseldorf.ktor.metrics.init
import no.nav.k9.los.eventhandler.*
import no.nav.k9.los.integrasjon.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.los.jobber.K9sakBehandlingsoppfriskingJobb
import no.nav.k9.los.jobbplanlegger.Jobbplanlegger
import no.nav.k9.los.jobbplanlegger.PlanlagtJobb
import no.nav.k9.los.jobbplanlegger.Tidsvindu
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.K9KlageTilLosApi
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.K9KlageTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.punsjtillos.K9PunsjTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.K9TilbakeTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.k9TilbakeEksternId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.k9tilbakeKorrigerOutOfOrderProsessor
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9v3
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.OppgavestatistikkTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkApi
import no.nav.k9.los.nyoppgavestyring.forvaltning.forvaltningApis
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoApis
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonApi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Api
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeApi
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheService
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryApis
import no.nav.k9.los.nyoppgavestyring.søkeboks.SøkeboksApi
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.NøkkeltallV3Apis
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederApis
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.NokkeltallApis
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.AvdelingslederOppgavekøApis
import no.nav.k9.los.tjenester.driftsmeldinger.DriftsmeldingerApis
import no.nav.k9.los.tjenester.fagsak.FagsakApis
import no.nav.k9.los.tjenester.innsikt.InnsiktApis
import no.nav.k9.los.tjenester.kodeverk.KodeverkApis
import no.nav.k9.los.tjenester.konfig.KonfigApis
import no.nav.k9.los.tjenester.mock.localSetup
import no.nav.k9.los.tjenester.saksbehandler.NavAnsattApis
import no.nav.k9.los.tjenester.saksbehandler.nokkeltall.SaksbehandlerNøkkeltallApis
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveApis
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
        modules(selectModuleBasedOnProfile(this@k9Los, config = configuration))
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

    environment.monitor.subscribe(ApplicationStopping) {
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
        behandlingProsessEventK9Repository = koin.get(),
        oppgavetypeTjeneste = koin.get(),
        oppgaveV3Tjeneste = koin.get(),
        config = koin.get(),
        transactionalManager = koin.get(),
        k9SakBerikerKlient = koin.get(),
        pepCacheService = koin.get(),
        oppgaveRepository = koin.get(),
        reservasjonV3Tjeneste = koin.get(),
        historikkvaskChannel = koin.get<Channel<k9SakEksternId>>(named("historikkvaskChannelK9Sak"))
    ).kjør(kjørSetup = false, kjørUmiddelbart = false)

    // implementer med Jobbplanlegger
    K9KlageTilLosAdapterTjeneste(
        behandlingProsessEventKlageRepository = koin.get(),
        områdeRepository = koin.get(),
        feltdefinisjonTjeneste = koin.get(),
        oppgavetypeTjeneste = koin.get(),
        oppgaveV3Tjeneste = koin.get(),
        transactionalManager = koin.get(),
        config = koin.get(),
        k9sakBeriker = koin.get(),
        k9klageBeriker = koin.get(),
    ).kjør(kjørSetup = false, kjørUmiddelbart = false)

    // implementer med Jobbplanlegger
    K9PunsjTilLosAdapterTjeneste(
        eventRepository = koin.get(),
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

    install(CallIdRequired)

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
    }

    install(Routing) {

        MetricsRoute()
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

    install(MicrometerMetrics) {
        init(appId)
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        call.request.log()
    }

    install(CallId) {
        fromXCorrelationIdHeader()
    }

    install(SwaggerUI)
}

private fun Route.api() {
    route("k9/los/api") {
        route("openapi.json") {
            openApiSpec()
        }
        swaggerUI("openapi.json")
        route("/forvaltning") {
            InnsiktApis()
            forvaltningApis()
            route("k9saktillos") { K9SakTilLosApi() }
            route("k9klagetillos") { K9KlageTilLosApi() }
            route("statistikk") { StatistikkApi() }
        }
    }
    route("api") {
        route("driftsmeldinger", { hidden = true }) {
            DriftsmeldingerApis()
        }
        route("fagsak", { hidden = true }) {
            FagsakApis()
        }
        route("saksbehandler", { hidden = true }) {
            route("oppgaver") {
                OppgaveApis()
            }

            SaksbehandlerOppgavekoApis()
            SaksbehandlerNøkkeltallApis()
        }
        route("avdelingsleder") {
            AvdelingslederApis()
            route("oppgavekoer") {
                AvdelingslederOppgavekøApis() // Erstattet av OppgaveKoApis i V3
            }
            route("nokkeltall") {
                NokkeltallApis()
            }
        }

        NavAnsattApis()

        route("konfig", { hidden = true }) { KonfigApis() }
        route("kodeverk") { KodeverkApis() }

        route("ny-oppgavestyring") {
            route("ko", { hidden = true }) { OppgaveKoApis() }
            route("oppgave", { hidden = true }) { OppgaveQueryApis() }
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

    val høyPrioritet = 0
    val mediumPrioritet = 5
    val lavPrioritet = 10
    val utvidetArbeidstid = Tidsvindu.hverdager(5, 20)

    val planlagteJobber = buildSet {
        add(
            PlanlagtJobb.KjørPåTidspunkt(
                "K9SakTilLosHistorikkvask",
                høyPrioritet,
                kjørTidligst = LocalDateTime.of(2025, 2, 27, 19, 0),
                kjørSenest = LocalDateTime.of(2025, 2, 28, 6, 0),
            ) {
                k9SakTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
        )

        add(
            PlanlagtJobb.KjørPåTidspunkt(
                "K9PunsjTilLosHistorikkvask",
                høyPrioritet,
                kjørTidligst = LocalDateTime.of(2025, 1, 1, 0, 0),
                kjørSenest = LocalDateTime.of(2025, 1, 1, 0, 1),
            ) {
                k9PunsjTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
        )

        add(
            PlanlagtJobb.KjørPåTidspunkt(
                "K9TilbakeTilLosHistorikkvask",
                høyPrioritet,
                kjørTidligst = LocalDateTime.of(2025, 2, 27, 17, 0),
                kjørSenest = LocalDateTime.of(2025, 2, 28, 6, 0),
            ) {
                k9TilbakeTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
        )

        add(
            PlanlagtJobb.KjørPåTidspunkt(
                "K9KlageTilLosHistorikkvask",
                høyPrioritet,
                kjørTidligst = LocalDateTime.of(2025, 3, 25, 10, 0),
                kjørSenest = LocalDateTime.of(2025, 3, 25, 20, 0),
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

        // Kjører ikke nøkkeltalloppdatering i prod inntil ytelsen er forbedret
        if (configuration.koinProfile != KoinProfile.PROD) {
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
                PlanlagtJobb.TimeJobb(
                    navn = "DagensTallOppdaterer",
                    prioritet = lavPrioritet,
                    tidsvindu = Tidsvindu.alleDager(5, 20),
                    minutter = listOf(0, 30),
                ) {
                    dagensTallService.oppdaterCache(this)
                }
            )

            add(
                PlanlagtJobb.TimeJobb(
                    navn = "PerEnhetOppdaterer",
                    prioritet = lavPrioritet,
                    tidsvindu = Tidsvindu.alleDager(7, 11),
                    minutter = listOf(15),
                ) {
                    perEnhetService.oppdaterCache(this)
                }
            )
        }
    }

    val jobbplanlegger = Jobbplanlegger(
        innkommendeJobber = planlagteJobber,
        coroutineContext = Dispatchers.IO.limitedParallelism(4) + Span.current().asContextElement(),
    )

    environment.monitor.subscribe(ApplicationStarted) {
        jobbplanlegger.start()
    }

    environment.monitor.subscribe(ApplicationStopping) {
        jobbplanlegger.stopp()
    }
}