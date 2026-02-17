@file:Suppress("USELESS_CAST")

package no.nav.k9.los

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering.ReservasjonKonverteringJobb
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.adhocjobber.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.AvstemmingsTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.HistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.OppgaveOppdatertHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.VaskeeventSerieutleder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.KlageEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.PunsjEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.SakEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker.K9SakSystemKlientInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker.K9SakSystemKlientLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.TilbakeEventTilOppgaveMapper
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.RefreshK9v3Tjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.IK9SakService
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient.K9SakServiceLocal
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.*
import no.nav.k9.los.nyoppgavestyring.driftsmelding.DriftsmeldingRepository
import no.nav.k9.los.nyoppgavestyring.feltutlederforlagring.GyldigeFeltutledere
import no.nav.k9.los.nyoppgavestyring.forvaltning.ForvaltningRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.PepClientLocal
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.AzureGraphServiceLocal
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PdlServiceLocal
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkRepository
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOgPartisjonertOppgaveAjourholdTjeneste
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
import no.nav.k9.los.nyoppgavestyring.uttrekk.UttrekkCsvGenerator
import no.nav.k9.los.nyoppgavestyring.uttrekk.UttrekkJobb
import no.nav.k9.los.nyoppgavestyring.uttrekk.UttrekkRepository
import no.nav.k9.los.nyoppgavestyring.uttrekk.UttrekkTjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.NøkkeltallRepositoryV3
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.OppgaverGruppertRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.status.StatusService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.statusfordeling.StatusFordelingService
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.*
import javax.sql.DataSource

fun buildAndTestConfig(dataSource: DataSource, pepClient: IPepClient = PepClientLocal()): Module = module {

    val config = mockk<Configuration>()

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

    single {
        K9SakServiceLocal() as IK9SakService
    }

    single { PepCacheRepository(dataSource) }
    single {
        PepCacheService(
            pepClient = get(),
            pepCacheRepository = get(),
            oppgaveRepository = get(),
            transactionalManager = get()
        )
    }

    single { dataSource }
    single { pepClient }

    single {
        AktivOppgaveRepository(
            oppgavetypeRepository = get()
        )
    }

    single { DriftsmeldingRepository(get()) }

    single {
        SaksbehandlerRepository(
            dataSource = get(),
            pepClient = get(),
            transactionalManager = get(),
        )
    }

    single {
        GyldigeFeltutledere(
            saksbehandlerRepository = get()
        )
    }

    single {
        config
    }
    every { config.koinProfile() } returns KoinProfile.LOCAL
    every { config.k9FrontendUrl() } returns "http://localhost:9000"
    every { config.k9PunsjFrontendUrl() } returns "http://localhost:8080"
    every { config.nyOppgavestyringAktivert() } returns true

    single {
        PdlServiceLocal() as IPdlService
    }
    single {
        AzureGraphServiceLocal(
        ) as IAzureGraphService
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
        ReservasjonOversetter(
            oppgaveV3RepositoryMedTxWrapper = get(),
        )
    }

    single { TransactionalManager(dataSource = get()) }

    single {
        K9SakEventHandler(
            transactionalManager = get(),
            eventTilOppgaveAdapter = get(),
            eventRepository = get(),
        )
    }

    single {
        K9KlageEventHandler(
            transactionalManager = get(),
            oppgaveAdapter = get(),
            eventRepository = get(),
        )
    }

    single {
        K9TilbakeEventHandler(
            transactionalManager = get(),
            oppgaveAdapter = get(),
            eventRepository = get()
        )
    }

    single {
        K9PunsjEventHandler(
            transactionalManager = get(),
            oppgaveAdapter = get(),
            eventRepository = get(),
        )
    }

    single {
        EventRepository(
            dataSource = get(),
        )
    }

    single {
        SaksbehandlerAdminTjeneste(
            pepClient = get(),
            transactionalManager = get(),
            saksbehandlerRepository = get(),
            oppgaveKøV3Repository = get(),
            lagretSøkTjeneste = get(),
            reservasjonV3Tjeneste = get(),
        )
    }

    single { FeltdefinisjonRepository(områdeRepository = get()) }
    single { OmrådeRepository(dataSource = get()) }
    single(createdAtStart = true) {
        OmrådeSetup(
            områdeRepository = get(),
            feltdefinisjonTjeneste = get(),
            oppgavetypeTjeneste = get(),
            config = get(),
        )
    }
    single { OppgavetypeRepository(dataSource = get(), feltdefinisjonRepository = get(), områdeRepository = get(), gyldigeFeltutledere = get()) }
    single { OppgaveV3Repository(dataSource = get(), oppgavetypeRepository = get()) }
    single { PartisjonertOppgaveRepository(oppgavetypeRepository = get()) }
    single { K9SakOppgaveTilDVHMapper() }
    single { K9KlageOppgaveTilDVHMapper() }
    single { OppgaveRepository(oppgavetypeRepository = get()) }
    single {
        StatistikkRepository(
            dataSource = get(),
            oppgavetypeRepository = get()
        )
    }

    single { NøkkeltallRepositoryV3(dataSource = get()) }

    single { OppgaverGruppertRepository(dataSource = get()) }

    single { mockk<StatistikkPublisher>() }

    single {
        OppgavestatistikkTjeneste(
            statistikkPublisher = get(),
            transactionalManager = get(),
            statistikkRepository = get(),
            pepClient = get(),
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
        EventRepository(
            dataSource = get(),
        )
    }

    single {
        EventTilOppgaveAdapter(
            eventRepository = get(),
            oppgaveV3Tjeneste = get(),
            transactionalManager = get(),
            eventTilOppgaveMapper = get(),
            oppgaveOppdatertHandler = get(),
            vaskeeventSerieutleder = get(),
            ajourholdTjeneste = get(),
        )
    }

    single {
        AktivOgPartisjonertOppgaveAjourholdTjeneste(
            partisjonertOppgaveRepository = get(),
        )
    }

    single {
        VaskeeventSerieutleder(
            sakEventTilOppgaveMapper = get(),
            klageEventTilOppgaveMapper = get()
        )
    }

    single {
        HistorikkvaskTjeneste(
            eventRepository = get(),
            oppgaveV3Tjeneste = get(),
            statistikkRepository = get(),
            eventTilOppgaveAdapter = get(),
            transactionalManager = get()
        )
    }

    single {
        EventTilOppgaveMapper(
            klageEventTilOppgaveMapper = get(),
            punsjEventTilOppgaveMapper = get(),
            sakEventTilOppgaveMapper = get(),
            tilbakeEventTilOppgaveMapper = get()
        )
    }

    single {
        SakEventTilOppgaveMapper(
            k9SakBerikerKlient = get(),
        )
    }

    single {
        KlageEventTilOppgaveMapper(
            k9klageBeriker = get()
        )
    }

    single {
        TilbakeEventTilOppgaveMapper()
    }

    single {
        PunsjEventTilOppgaveMapper()
    }

    single {
        OppgaveOppdatertHandler(
            oppgaveRepository = get(),
            reservasjonV3Tjeneste = get(),
            eventTilOppgaveMapper = get(),
            pepCacheService = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single<K9SakSystemKlientInterfaceKludge> {
        K9SakSystemKlientLocal()
    }

    single<K9KlageBerikerInterfaceKludge> {
        K9KlageBerikerKlientLocal()
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
            oppgaveV3Repository = get(),
            pepClient = get(),
            saksbehandlerRepository = get(),
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        )
    }

    single {
        ReservasjonV3DtoBuilder(
            pdlService = get(),
            saksbehandlerRepository = get()
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
        ReservasjonApisTjeneste(
            saksbehandlerRepository = get(),
            reservasjonV3Tjeneste = get(),
            oppgaveV3Repository = get(),
            transactionalManager = get(),
            reservasjonV3DtoBuilder = get(),
            reservasjonOversetter = get(),
            pepClient = get(),
            azureGraphService = get(),
        )
    }

    single {
        PepCacheRepository(dataSource = get())
    }

    single {
        OppgaveRepositoryTxWrapper(
            oppgaveRepository = get(),
            transactionalManager = get(),
        )
    }

    single<ForvaltningRepository> {
        ForvaltningRepository(
            oppgavetypeRepository = get(),
            transactionalManager = get(),
        )
    }

    single<AvstemmingsTjeneste> {
        AvstemmingsTjeneste(
            oppgaveQueryService = get(),
            k9SakAvstemmingsklient = get(),
            k9KlageAvstemmingsklient = get(),
            k9PunsjAvstemmingsklient = get(),
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
            saksbehandlerRepository = get(),
            pepClient = get(),
        )
    }

    single {
        SisteOppgaverRepository(dataSource = get())
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
            queryService = get()
        )
    }

    single {
        NyeOgFerdigstilteService(
            queryService = get()
        )
    }

    single {
        StatusFordelingService(
            queryService = get()
        )
    }

    single<LagretSøkRepository> {
        LagretSøkRepository(dataSource = get())
    }

    single<LagretSøkTjeneste> {
        LagretSøkTjeneste(
            saksbehandlerRepository = get(),
            lagretSøkRepository = get(),
            oppgaveQueryService = get()
        )
    }

    single<UttrekkRepository> {
        UttrekkRepository(dataSource = get())
    }

    single<UttrekkTjeneste> {
        UttrekkTjeneste(
            uttrekkRepository = get(),
            lagretSøkRepository = get()
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
}
