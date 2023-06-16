@file:Suppress("USELESS_CAST")

package no.nav.k9.los

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.channels.Channel
import no.nav.k9.los.aksjonspunktbehandling.K9KlageEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9TilbakeEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.los.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.abac.PepClientLocal
import no.nav.k9.los.integrasjon.azuregraph.AzureGraphServiceLocal
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.datavarehus.StatistikkProducer
import no.nav.k9.los.integrasjon.k9.IK9SakService
import no.nav.k9.los.integrasjon.k9.K9SakServiceLocal
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.pdl.PdlServiceLocal
import no.nav.k9.los.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederTjeneste
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.NokkeltallTjeneste
import no.nav.k9.los.tjenester.saksbehandler.merknad.MerknadTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveKøOppdaterer
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import no.nav.k9.los.tjenester.sse.SseEvent
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.*
import javax.sql.DataSource

fun buildAndTestConfig(dataSource: DataSource, pepClient: IPepClient = PepClientLocal()): Module = module {

    single(named("oppgaveKøOppdatert")) {
        Channel<UUID>(Channel.UNLIMITED)
    }
    single(named("refreshKlienter")) {
        Channel<SseEvent>(Channel.UNLIMITED)
    }
    single(named("oppgaveChannel")) {
        Channel<Oppgave>(Channel.UNLIMITED)
    }
    single(named("oppgaveRefreshChannel")) {
        Channel<Oppgave>(Channel.UNLIMITED)
    }
    single(named("statistikkRefreshChannel")) {
        Channel<Boolean>(Channel.CONFLATED)
    }
    single {
        K9SakServiceLocal() as IK9SakService
    }

    single { dataSource }
    single { pepClient }
    single {
        no.nav.k9.los.domene.repository.OppgaveRepository(
            dataSource = get(),
            pepClient = get(),
            refreshOppgave = get(named("oppgaveRefreshChannel"))
        )
    }
    single { DriftsmeldingRepository(get()) }
    single { no.nav.k9.los.domene.repository.StatistikkRepository(get()) }

    single {
        OppgaveKøRepository(
            dataSource = get(),
            oppgaveRepositoryV2 = get(),
            oppgaveKøOppdatert = get(named("oppgaveKøOppdatert")),
            refreshKlienter = get(named("refreshKlienter")),
            oppgaveRefreshChannel = get(named("oppgaveRefreshChannel")),
            pepClient = get()
        )
    }
    single {
        SaksbehandlerRepository(
            dataSource = get(),
            pepClient = get()
        )
    }

    single {
        ReservasjonRepository(
            oppgaveKøRepository = get(),
            oppgaveRepository = get(),
            oppgaveRepositoryV2 = get(),
            dataSource = get(),
            refreshKlienter = get(named("refreshKlienter")),
            saksbehandlerRepository = get()
        )
    }
    val config = mockk<Configuration>()
    single {
        config
    }
    every { config.koinProfile() } returns KoinProfile.LOCAL

    single {
        PdlServiceLocal() as IPdlService
    }
    single {
        AzureGraphServiceLocal(
        ) as IAzureGraphService
    }
    single {
        OppgaveTjeneste(
            oppgaveRepository = get(),
            oppgaveRepositoryV2 = get(),
            oppgaveKøRepository = get(),
            saksbehandlerRepository = get(),
            pdlService = get(),
            reservasjonRepository = get(),
            configuration = get(),
            azureGraphService = get(),
            pepClient = get(),
            statistikkRepository = get()
        )
    }

    single { OppgaveRepositoryV2(dataSource = get()) }
    single { TransactionalManager(dataSource = get()) }
    single {
        OppgaveTjenesteV2(
            oppgaveRepository = get(),
            migreringstjeneste = get(),
            tm = get()
        )
    }

    single { BehandlingsmigreringTjeneste(BehandlingProsessEventK9Repository(dataSource = get())) }

    single {
        NokkeltallTjeneste(
            oppgaveRepository = get(),
            statistikkRepository = get()
        )
    }

    val sakOgBehadlingProducer = mockk<SakOgBehandlingProducer>()
    val statistikkProducer = mockk<StatistikkProducer>()
    every { sakOgBehadlingProducer.behandlingOpprettet(any()) } just runs
    every { sakOgBehadlingProducer.avsluttetBehandling(any()) } just runs
    every { statistikkProducer.send(any()) } just runs

    single {
        K9sakEventHandler(
            get(),
            BehandlingProsessEventK9Repository(dataSource = get()),
            sakOgBehandlingProducer = sakOgBehadlingProducer,
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            statistikkProducer = statistikkProducer,
            statistikkChannel = get(named("statistikkRefreshChannel")),
            statistikkRepository = get(),
            reservasjonTjeneste = get(),
            k9SakTilLosAdapterTjeneste = get(),
        )
    }

    single {
        K9KlageEventHandler(
            BehandlingProsessEventKlageRepository(dataSource = get()),
            k9KlageTilLosAdapterTjeneste = get()
        )
    }

    single {
        K9TilbakeEventHandler(
            get(),
            BehandlingProsessEventTilbakeRepository(dataSource = get()),
            sakOgBehandlingProducer = sakOgBehadlingProducer,
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            statistikkRepository = get(),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            reservasjonTjeneste = get()
        )
    }

    single {
        ReservasjonTjeneste(
            reservasjonRepository = get(),
            saksbehandlerRepository = get()
        )
    }

    single {
        K9punsjEventHandler(
            oppgaveRepository = get(),
            oppgaveTjenesteV2 = get(),
            punsjEventK9Repository = PunsjEventK9Repository(dataSource = get()),
            statistikkChannel = get(named("statistikkRefreshChannel")),
            oppgaveKøRepository = get(),
            reservasjonRepository = get(),
            reservasjonTjeneste = get(),
            statistikkRepository = get(),
            azureGraphService = get()
        )
    }

    single {
        MerknadTjeneste(
            oppgaveRepositoryV2 = get(),
            azureGraphService = get(),
            oppgaveKøOppdaterer = get(),
            migreringstjeneste = get(),
            k9SakTilLosAdapterTjeneste = get(),
            behandlingProsessEventK9Repository = get(),
            tm = get()
        )
    }

    single { OppgaveKøOppdaterer(get(), get(), get()) }

    single {
        AvdelingslederTjeneste(
            oppgaveKøRepository = get(),
            saksbehandlerRepository = get(),
            oppgaveTjeneste = get(),
            reservasjonRepository = get(),
            oppgaveRepository = get(),
            pepClient = get(),
            configuration = get()
        )
    }

    single { FeltdefinisjonRepository(områdeRepository = get()) }
    single { OmrådeRepository(dataSource = get()) }
    single(createdAtStart = true) {
        OmrådeSetup(
            områdeRepository = get(),
            feltdefinisjonTjeneste = get()
        ).setup()
    }
    single { OppgavetypeRepository(feltdefinisjonRepository = get(), områdeRepository = get()) }
    single { OppgaveV3Repository(dataSource = get(), oppgavetypeRepository = get()) }
    single { BehandlingProsessEventK9Repository(dataSource = get()) }
    single { BehandlingProsessEventKlageRepository(dataSource = get()) }
    single { K9SakOppgaveTilDVHMapper() }
    single { K9KlageOppgaveTilDVHMapper() }
    single { OppgaveRepository(oppgavetypeRepository = get()) }
    single { StatistikkRepository(dataSource = get()) }

    single { mockk<StatistikkPublisher>() }

    single {
        OppgavestatistikkTjeneste(
            oppgaveRepository = get(),
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
            transactionalManager = get()
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
        K9SakTilLosAdapterTjeneste(
            behandlingProsessEventK9Repository = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            oppgaveRepositoryV2 = get(),
            transactionalManager = get(),
            k9SakBerikerKlient = get(),
        ).setup()
    }

    single<K9SakBerikerInterfaceKludge> {
        K9SakBerikerKlientLocal()
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
        OppgaveKoRepository(
            datasource = get()
        )
    }

    single {
        PepCacheRepository(dataSource = get())
    }
}
