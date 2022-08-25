@file:Suppress("USELESS_CAST")

package no.nav.k9

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.channels.Channel
import no.nav.k9.aksjonspunktbehandling.K9TilbakeEventHandler
import no.nav.k9.aksjonspunktbehandling.K9punsjEventHandler
import no.nav.k9.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.v2.BehandlingsmigreringTjeneste
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.lager.oppgave.v2.OppgaveTjenesteV2
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.domene.lager.oppgave.v3.omraade.OmrådeRepository
import no.nav.k9.domene.lager.oppgave.v3.omraade.OmrådeService
import no.nav.k9.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.domene.repository.BehandlingProsessEventTilbakeRepository
import no.nav.k9.domene.repository.DriftsmeldingRepository
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.PunsjEventK9Repository
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.domene.repository.SaksbehandlerRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.integrasjon.abac.IPepClient
import no.nav.k9.integrasjon.abac.PepClientLocal
import no.nav.k9.integrasjon.azuregraph.AzureGraphServiceLocal
import no.nav.k9.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.integrasjon.datavarehus.StatistikkProducer
import no.nav.k9.integrasjon.k9.IK9SakService
import no.nav.k9.integrasjon.k9.K9SakServiceLocal
import no.nav.k9.integrasjon.omsorgspenger.IOmsorgspengerService
import no.nav.k9.integrasjon.omsorgspenger.OmsorgspengerServiceLocal
import no.nav.k9.integrasjon.pdl.IPdlService
import no.nav.k9.integrasjon.pdl.PdlServiceLocal
import no.nav.k9.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.tjenester.avdelingsleder.AvdelingslederTjeneste
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.NokkeltallTjeneste
import no.nav.k9.tjenester.saksbehandler.merknad.MerknadTjeneste
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveKøOppdaterer
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import no.nav.k9.tjenester.sse.SseEvent
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

    single {
        OmsorgspengerServiceLocal() as IOmsorgspengerService
    }

    single { dataSource }
    single { pepClient }
    single {
        OppgaveRepository(
            dataSource = get(),
            pepClient = get(),
            refreshOppgave = get(named("oppgaveRefreshChannel"))
        )
    }
    single { DriftsmeldingRepository(get()) }
    single { StatistikkRepository(get()) }

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
            statistikkRepository = get(),
            omsorgspengerService = get()
        )
    }

    single { OppgaveRepositoryV2(dataSource = get()) }
    single { TransactionalManager(dataSource = get()) }
    single { OppgaveTjenesteV2(
        oppgaveRepository = get(),
        migreringstjeneste = get(),
        tm = get())
    }

    single { BehandlingsmigreringTjeneste(BehandlingProsessEventK9Repository(dataSource = get()))}

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
            reservasjonTjeneste = get()
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

    single { OmrådeRepository(get()) }
    single { OmrådeService(områdeRepository = get()) }
}
