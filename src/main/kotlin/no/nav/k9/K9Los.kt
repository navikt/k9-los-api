package no.nav.k9

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.jackson.*
import io.ktor.locations.*
import io.ktor.metrics.micrometer.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
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
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.dusseldorf.ktor.metrics.init
import no.nav.k9.eventhandler.køOppdatertProsessor
import no.nav.k9.eventhandler.oppdaterStatistikk
import no.nav.k9.eventhandler.refreshK9
import no.nav.k9.eventhandler.sjekkReserverteJobb
import no.nav.k9.integrasjon.datavarehus.StatistikkProducer
import no.nav.k9.integrasjon.kafka.AsynkronProsesseringV1Service
import no.nav.k9.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.jobber.rekjørEventerForGrafer
import no.nav.k9.jobber.rekjørEventerForGraferPunsj
import no.nav.k9.tjenester.admin.AdminApis
import no.nav.k9.tjenester.avdelingsleder.AvdelingslederApis
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.NokkeltallApis
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.AvdelingslederOppgavekøApis
import no.nav.k9.tjenester.driftsmeldinger.DriftsmeldingerApis
import no.nav.k9.tjenester.fagsak.FagsakApis
import no.nav.k9.tjenester.innsikt.innsiktGrensesnitt
import no.nav.k9.tjenester.kodeverk.KodeverkApis
import no.nav.k9.tjenester.konfig.KonfigApis
import no.nav.k9.tjenester.mock.MockGrensesnitt
import no.nav.k9.tjenester.saksbehandler.NavAnsattApis
import no.nav.k9.tjenester.saksbehandler.nokkeltall.SaksbehandlerNøkkeltallApis
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveApis
import no.nav.k9.tjenester.saksbehandler.saksliste.SaksbehandlerOppgavekoApis
import no.nav.k9.tjenester.sse.RefreshKlienterWebSocket
import no.nav.k9.tjenester.sse.SseEvent
import org.koin.core.qualifier.named
import org.koin.ktor.ext.Koin
import org.koin.ktor.ext.getKoin
import java.time.Duration
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
    install(Authentication) {
        multipleJwtIssuers(issuers)
    }

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
                .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
                .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
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
            channel = koin.get<Channel<UUID>>(named("oppgaveKøOppdatert")),
            reservasjonRepository = koin.get(),
            k9SakService = koin.get(),
            oppgaveTjeneste = koin.get()
        )


    val refreshOppgaveJobb =
        refreshK9(
            channel = koin.get<Channel<UUID>>(named("oppgaveRefreshChannel")),
            k9SakService = koin.get(),
            oppgaveRepository = koin.get()
        )

    val oppdaterStatistikkJobb =
        oppdaterStatistikk(
            channel = koin.get<Channel<Boolean>>(named("statistikkRefreshChannel")),
            statistikkRepository = koin.get(),
            oppgaveTjeneste = koin.get(),
            oppgaveKøRepository = koin.get()
        )

    val sjekkReserverteJobb =
        sjekkReserverteJobb(saksbehandlerRepository = koin.get(), reservasjonRepository = koin.get())

    val asynkronProsesseringV1Service = koin.get<AsynkronProsesseringV1Service>()
    val sakOgBehadlingProducer = koin.get<SakOgBehandlingProducer>()
    val statistikkProducer = koin.get<StatistikkProducer>()

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
    }

    // Server side events
    val sseChannel = produce {
        for (oppgaverOppdatertEvent in koin.get<Channel<SseEvent>>(named("refreshKlienter"))) {
            send(oppgaverOppdatertEvent)
        }
    }.broadcast()

//  Synkroniser oppgaver
//    regenererOppgaver(
//        oppgaveRepository = koin.get(),
//        behandlingProsessEventK9Repository = koin.get(),
//        reservasjonRepository = koin.get(),
//        oppgaveKøRepository = koin.get(),
//        saksbehhandlerRepository = koin.get(),
//        punsjEventK9Repository = koin.get(),
//        behandlingProsessEventTilbakeRepository = koin.get()
//    )
    rekjørEventerForGraferPunsj(koin.get(), koin.get())

    install(CallIdRequired)

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
    }

    install(Locations)

    install(Routing) {

        MetricsRoute()
        DefaultProbeRoutes()

        HealthReporter(
            app = appId,
            healthService = koin.get(),
            frequency = Duration.ofMinutes(1)
        )

        route("innsikt") {
            innsiktGrensesnitt()
        }

        if ((KoinProfile.LOCAL == koin.get<KoinProfile>())) {
            api(sseChannel)
            route("mock") {
                MockGrensesnitt()
            }
        } else {
            authenticate(*issuers.allIssuers()) {
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
}

private fun Route.api(sseChannel: BroadcastChannel<SseEvent>) {

    RefreshKlienterWebSocket(
        sseChannel = sseChannel
    )

    route("api") {

        AdminApis()
        route("driftsmeldinger") {
            DriftsmeldingerApis()
        }
        route("fagsak") {
            FagsakApis()
        }
        route("saksbehandler") {
            route("oppgaver") {
                OppgaveApis()
            }

            SaksbehandlerOppgavekoApis()
            SaksbehandlerNøkkeltallApis()
        }
        route("avdelingsleder") {
            AvdelingslederApis()
            route("oppgavekoer") {
                AvdelingslederOppgavekøApis()
            }
            route("nokkeltall") {
                NokkeltallApis()
            }
        }

        NavAnsattApis()

        route("konfig") { KonfigApis() }
        KodeverkApis()
    }
}




