package no.nav.k9.los

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.locations.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.produce
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
import no.nav.k9.los.eventhandler.RefreshK9
import no.nav.k9.los.eventhandler.køOppdatertProsessor
import no.nav.k9.los.eventhandler.oppdaterStatistikk
import no.nav.k9.los.eventhandler.sjekkReserverteJobb
import no.nav.k9.los.integrasjon.datavarehus.StatistikkProducer
import no.nav.k9.los.integrasjon.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.los.jobber.K9sakBehandlingsoppfriskingJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.klagetillos.K9KlageTilLosApi
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosApi
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.k9SakEksternId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.k9SakKorrigerOutOfOrderProsessor
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.OppgavestatistikkTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkApi
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9klagetillos.K9KlageTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.forvaltning.forvaltningApis
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoApis
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonApi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Api
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeApi
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheOppdaterer
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryApis
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederApis
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.DataeksportApis
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.NokkeltallApis
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.AvdelingslederOppgavekøApis
import no.nav.k9.los.tjenester.driftsmeldinger.DriftsmeldingerApis
import no.nav.k9.los.tjenester.fagsak.FagsakApis
import no.nav.k9.los.tjenester.innsikt.innsiktGrensesnitt
import no.nav.k9.los.tjenester.kodeverk.KodeverkApis
import no.nav.k9.los.tjenester.konfig.KonfigApis
import no.nav.k9.los.tjenester.mock.localSetup
import no.nav.k9.los.tjenester.saksbehandler.NavAnsattApis
import no.nav.k9.los.tjenester.saksbehandler.merknad.MerknadApi
import no.nav.k9.los.tjenester.saksbehandler.nokkeltall.SaksbehandlerNøkkeltallApis
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveApis
import no.nav.k9.los.tjenester.saksbehandler.saksliste.SaksbehandlerOppgavekoApis
import no.nav.k9.los.tjenester.sse.RefreshKlienterWebSocket
import no.nav.k9.los.tjenester.sse.SseEvent
import org.koin.core.qualifier.named
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

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

    val områdeSetup = koin.get<OmrådeSetup>()
    områdeSetup.setup()
    val k9SakTilLosAdapterTjeneste = koin.get<K9SakTilLosAdapterTjeneste>()
    k9SakTilLosAdapterTjeneste.setup()
    val k9KlageTilLosAdapterTjeneste = koin.get<K9KlageTilLosAdapterTjeneste>()
    k9KlageTilLosAdapterTjeneste.setup()
    val k9PunsjTilLosAdapterTjeneste = koin.get<K9PunsjTilLosAdapterTjeneste>()
    k9PunsjTilLosAdapterTjeneste.setup()

    if (LocalDateTime.now().isBefore(LocalDateTime.of(2024, 7, 14, 20, 0))) {
        if (1 == 0) { //HAXX for å ikke kjøre jobb, men indikere at koden er i bruk og dermed ikke slettes
            koin.get<K9SakTilLosHistorikkvaskTjeneste>().kjørHistorikkvask()
            koin.get<K9KlageTilLosHistorikkvaskTjeneste>().kjørHistorikkvask()
            koin.get<OppgavestatistikkTjeneste>().slettStatistikkgrunnlag()
            //koin.get<ReservasjonKonverteringJobb>().kjørReservasjonskonvertering() //TODO slette
            //koin.get<K9SakTilLosLukkeFeiloppgaverTjeneste>().kjørFeiloppgaverVask() //TODO slette
        }
        val historikkvaskTjeneste = koin.get<K9SakTilLosHistorikkvaskTjeneste>()
        historikkvaskTjeneste.kjørHistorikkvask()
    }

    install(Authentication) {
        multipleJwtIssuers(issuers)
    }

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
                .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
        AuthStatusPages()
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(60)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
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

    val refreshOppgaveJobb = with(RefreshK9(
        k9SakService = koin.get(),
        oppgaveRepository = koin.get(),
        transactionalManager = koin.get()
    )) { start(koin.get<Channel<UUID>>(named("oppgaveRefreshChannel"))) }

    val oppdaterStatistikkJobb =
        oppdaterStatistikk(
            channel = koin.get<Channel<Boolean>>(named("statistikkRefreshChannel")),
            configuration = configuration,
            statistikkRepository = koin.get(),
            oppgaveTjeneste = koin.get()
        )

    PepCacheOppdaterer(koin.get()).run {
        startOppdateringAvÅpneOgVentende()
        startOppdateringAvLukkedeOppgaver()
    }

    K9sakBehandlingsoppfriskingJobb(
        oppgaveRepository = koin.get(),
        oppgaveKøRepository = koin.get(),
        reservasjonRepository = koin.get(),
        refreshOppgaveChannel = koin.get<Channel<UUID>>(named("oppgaveRefreshChannel")),
        configuration = koin.get()
    ).run { start() }

    val sjekkReserverteJobb =
        sjekkReserverteJobb(saksbehandlerRepository = koin.get(), reservasjonRepository = koin.get())

    val asynkronProsesseringV1Service = koin.get<AsynkronProsesseringV1Service>()
    val sakOgBehadlingProducer = koin.get<SakOgBehandlingProducer>()
    val statistikkProducer = koin.get<StatistikkProducer>()

    val k9SakKorrigerOutOfOrderProsessor =
        k9SakKorrigerOutOfOrderProsessor(
            k9SakTilLosHistorikkvaskTjeneste = koin.get(),
            channel = koin.get<Channel<k9SakEksternId>>(named("historikkvaskChannelK9Sak")),
        )

    environment.monitor.subscribe(ApplicationStopping) {
        log.info("Stopper AsynkronProsesseringV1Service.")
        asynkronProsesseringV1Service.stop()
        sakOgBehadlingProducer.stop()
        statistikkProducer.stop()
        sjekkReserverteJobb.cancel()
        log.info("AsynkronProsesseringV1Service Stoppet.")
        log.info("Stopper pipeline")
        køOppdatertProsessorJob.cancel()
        refreshOppgaveJobb.cancel()
        oppdaterStatistikkJobb.cancel()
        k9SakKorrigerOutOfOrderProsessor.cancel()
    }

    OmrådeSetup(
        områdeRepository = koin.get(),
        feltdefinisjonTjeneste = koin.get()
    )

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

    K9KlageTilLosAdapterTjeneste(
        behandlingProsessEventKlageRepository = koin.get(),
        områdeRepository = koin.get(),
        feltdefinisjonTjeneste = koin.get(),
        oppgavetypeTjeneste = koin.get(),
        oppgaveV3Tjeneste = koin.get(),
        transactionalManager = koin.get(),
        config = koin.get(),
        k9sakBeriker = koin.get(),
    ).kjør(kjørSetup = false, kjørUmiddelbart = false)

    K9PunsjTilLosAdapterTjeneste(
        eventRepository = koin.get(),
        oppgavetypeTjeneste = koin.get(),
        oppgaveV3Tjeneste = koin.get(),
        reservasjonV3Tjeneste = koin.get(),
        config = koin.get(),
        transactionalManager = koin.get(),
        pepCacheService = koin.get()
    ).kjør(kjørUmiddelbart = false)

    OppgavestatistikkTjeneste(
        oppgavetypeRepository = koin.get(),
        statistikkPublisher = koin.get(),
        transactionalManager = koin.get(),
        statistikkRepository = koin.get(),
        pepClient = koin.get(),
        config = koin.get()
    ).kjør(kjørUmiddelbart = false)

    // Server side events
    val sseChannel = produce {
        for (oppgaverOppdatertEvent in koin.get<Channel<SseEvent>>(named("refreshKlienter"))) {
            send(oppgaverOppdatertEvent)
        }
    }.broadcast()

    install(CallIdRequired)

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
    }

    install(Locations)

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
            api(sseChannel)
            route("/forvaltning") {
                innsiktGrensesnitt()
                forvaltningApis()
                route("k9saktillos") { K9SakTilLosApi() }
                route("k9klagetillos") { K9KlageTilLosApi() }
                route("statistikk") { StatistikkApi() }
                route("/swagger") {
                    route("openapi.json") {
                        openApiSpec()
                    }
                    swaggerUI("openapi.json")
                }
            }
        } else {
            authenticate(*issuers.allIssuers()) {
                route("forvaltning") {
                    innsiktGrensesnitt()
                    forvaltningApis()
                    route("k9saktillos") { K9SakTilLosApi() }
                    route("k9klagetillos") { K9KlageTilLosApi() }
                    route("statistikk") { StatistikkApi() }
                    route("/swagger") {
                        route("openapi.json") {
                            openApiSpec()
                        }
                        swaggerUI("openapi.json")
                    }
                }
                api(sseChannel)
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

    install(SwaggerUI) {
    }
}

private fun Route.api(sseChannel: BroadcastChannel<SseEvent>) {

    RefreshKlienterWebSocket(
        sseChannel = sseChannel
    )

    route("api", {
        hidden = true
    }) {
        route("driftsmeldinger") {
            DriftsmeldingerApis()
        }
        route("fagsak") {
            FagsakApis()
        }
        route("saksbehandler") {
            route("oppgaver") {
                OppgaveApis()
                MerknadApi()
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
                DataeksportApis()
            }
        }

        NavAnsattApis()

        route("konfig") { KonfigApis() }
        KodeverkApis()

        route("ny-oppgavestyring") {
            route("ko") { OppgaveKoApis() }
            route("oppgave") { OppgaveQueryApis() }
            route("feltdefinisjon") { FeltdefinisjonApi() }
            route("oppgavetype") { OppgavetypeApi() }
            route("oppgave-v3") { OppgaveV3Api() }
        }
    }
}
