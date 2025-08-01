package no.nav.k9.los

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.java.*
import io.ktor.server.application.*
import kotlinx.coroutines.channels.Channel
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.k9.los.KoinProfile.*
import no.nav.k9.los.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.fagsystem.k9sak.AksjonspunktHendelseMapper
import no.nav.k9.los.fagsystem.k9sak.K9sakEventHandlerV2
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.aktivvask.Aktivvask
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering.ReservasjonKonverteringJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.AsynkronProsesseringV1Service
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.K9KlageTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerSystemKlient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosLukkeFeiloppgaverTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker.K9SakBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker.K9SakBerikerSystemKlient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.k9SakEksternId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.K9TilbakeTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.k9TilbakeEksternId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9v3Tjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.IK9SakService
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakBehandlingOppfrisketRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakServiceLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakServiceSystemClient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.K9KlageOppgaveTilDVHMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.K9SakOppgaveTilDVHMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.OppgavestatistikkTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkPublisher
import no.nav.k9.los.nyoppgavestyring.driftsmelding.DriftsmeldingRepository
import no.nav.k9.los.nyoppgavestyring.driftsmelding.DriftsmeldingTjeneste
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.forvaltning.ForvaltningRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.audit.Auditlogger
import no.nav.k9.los.nyoppgavestyring.infrastruktur.audit.K9Auditlogger
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
import no.nav.k9.los.nyoppgavestyring.kodeverk.HentKodeverkTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
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
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.NøkkeltallRepositoryV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.status.StatusService
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederTjeneste
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.NokkeltallTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveKøOppdaterer
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import no.nav.k9.los.tjenester.saksbehandler.saksliste.SakslisteTjeneste
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
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
    single {
        NokkeltallTjeneste(
            pepClient = get(),
            oppgaverGruppertRepository = get(),
            oppgaveRepository = get(),
            statistikkRepository = get(),
            nøkkeltallRepository = get(),
            nøkkeltallRepositoryV3 = get(),
            koinProfile = config.koinProfile(),
        )
    }
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

    single { OppgaveRepository(get(), get(), get(named("oppgaveRefreshChannel"))) }

    single {
        AktivOppgaveRepository(
            oppgavetypeRepository = get()
        )
    }

    single {
        OppgaveKøRepository(
            dataSource = get(),
            oppgaveRepositoryV2 = get(),
            oppgaveKøOppdatert = get(named("oppgaveKøOppdatert")),
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
        ReservasjonRepository(
            oppgaveKøRepository = get(),
            oppgaveRepository = get(),
            oppgaveRepositoryV2 = get(),
            saksbehandlerRepository = get(),
            dataSource = get()
        )
    }

    single {
        no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventRepository(get())
    }

    single {
        no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventRepository(get())
    }

    single {
        no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository(get())
    }

    single {
        no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventRepository(get())
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
        no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkRepository(get(), get())
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
        K9sakEventHandlerV2(
            oppgaveTjenesteV2 = get(),
            aksjonspunktHendelseMapper = get()
        )
    }

    single {
        no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventHandler(
            oppgaveRepository = get(),
            k9SakEventRepository = get(),
            sakOgBehandlingProducer = get(),
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            statistikkRepository = get(),
            reservasjonTjeneste = get(),
            k9SakTilLosAdapterTjeneste = get(),
        )
    }

    single {
        no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventHandler(
            behandlingProsessEventKlageRepository = get(),
            k9KlageTilLosAdapterTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single {
        no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventHandler(
            oppgaveRepository = get(),
            behandlingProsessEventTilbakeRepository = get(),
            sakOgBehandlingProducer = get(),
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            statistikkRepository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            reservasjonTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
            k9TilbakeTilLosAdapterTjeneste = get(),
        )
    }

    single {
        no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler(
            oppgaveRepository = get(),
            oppgaveTjenesteV2 = get(),
            punsjEventK9Repository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            reservasjonTjeneste = get(),
            statistikkRepository = get(),
            azureGraphService = get(),
            punsjTilLosAdapterTjeneste = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
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
            k9PunsjEventHandler = get(),
            k9KlageEventHandler = get(),
        )
    }

    single {
        OppgaveTjeneste(
            oppgaveRepository = get(),
            oppgaverGruppertRepository = get(),
            oppgaveKøRepository = get(),
            saksbehandlerRepository = get(),
            reservasjonRepository = get(),
            pdlService = get(),
            configuration = config,
            pepClient = get(),
            azureGraphService = get(),
            statistikkRepository = get(),
            reservasjonOversetter = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            koinProfile = config.koinProfile,
        )
    }

    single {
        ReservasjonOversetter(
            transactionalManager = get(),
            oppgaveV3Repository = get(),
            reservasjonV3Tjeneste = get(),
            oppgaveV1Repository = get(),
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
            pepClient = get(),
            reservasjonV3Tjeneste = get(),
        )
    }

    single {
        SaksbehandlerAdminTjeneste(
            pepClient = get(),
            transactionalManager = get(),
            saksbehandlerRepository = get(),
            oppgaveKøV3Repository = get(),

            oppgaveKøRepository = get(),
            oppgaveTjeneste = get(),
        )
    }

    single {
        ReservasjonV3DtoBuilder(
            pdlService = get(),
            oppgaveTjeneste = get(),
            saksbehandlerRepository = get()
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

    single { OppgaveKøOppdaterer(get(), get(), get()) }

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
        StatistikkRepository(
            dataSource = get(),
        )
    }
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
            partisjonertOppgaveRepository = get(),
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
            feltdefinisjonTjeneste = get()
        )
    }

    single {
        K9PunsjTilLosAdapterTjeneste(
            k9PunsjEventRepository = get(),
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
            k9SakEventRepository = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            k9SakBerikerKlient = get(),
            pepCacheService = get(),
            oppgaveRepository = get(),
            reservasjonV3Tjeneste = get(),
            historikkvaskChannel = get(named("historikkvaskChannelK9Sak")),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
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
            oppgaveRepository = get(),
            oppgaveRepositoryTxWrapper = get(),
            reservasjonV3Tjeneste = get(),
            saksbehandlerRepository = get(),
            oppgaveTjeneste = get(),
            reservasjonRepository = get(),
            pdlService = get(),
            pepClient = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
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
            k9KlageEventRepository = get(),
            områdeRepository = get(),
            feltdefinisjonTjeneste = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            transactionalManager = get(),
            config = get(),
            k9klageBeriker = get(),
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
            k9PunsjEventRepository = get(),
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
            k9klageBeriker = get(),
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
        ReservasjonApisTjeneste(
            oppgaveTjeneste = get(),
            oppgaveV1Repository = get(),
            saksbehandlerRepository = get(),
            reservasjonV3Tjeneste = get(),
            oppgaveV3Repository = get(),
            transactionalManager = get(),
            reservasjonV3DtoBuilder = get(),
            reservasjonOversetter = get(),
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
            reservasjonV3Tjeneste = get(),
            saksbehandlerRepository = get()
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
            enheter = config.enheter(),
            queryService = get()
        )
    }

    single {
        NyeOgFerdigstilteService(
            queryService = get()
        )
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

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerKlientLocal()
    }

    single<K9KlageBerikerInterfaceKludge> {
        K9KlageBerikerKlientLocal()
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
        PepClient(azureGraphService = get(), k9Auditlogger = K9Auditlogger(Auditlogger(config)), get())
    }
}

// Unik konfigurasjon for preprod
fun preprodConfig(config: Configuration) = module {
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

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerSystemKlient(
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

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerSystemKlient(
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
}
