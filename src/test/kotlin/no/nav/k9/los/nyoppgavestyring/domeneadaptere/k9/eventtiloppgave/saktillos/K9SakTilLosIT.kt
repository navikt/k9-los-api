package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos

import assertk.assertThat
import assertk.assertions.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.K9SakEventDtoBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.TestSaksbehandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.builder
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.CoroutineRequestContext
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveMuligReservert
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.tjenester.saksbehandler.IIdToken
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveApisTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveIdMedOverstyringDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*


class K9SakTilLosIT : AbstractK9LosIntegrationTest() {

    lateinit var k9SakEventHandler: K9SakEventHandler
    lateinit var oppgaveKøTjeneste: OppgaveKoTjeneste
    lateinit var pepClient: IPepClient

    @BeforeEach
    fun setup() {
        k9SakEventHandler = get<K9SakEventHandler>()
        oppgaveKøTjeneste = get<OppgaveKoTjeneste>()
        pepClient = get<IPepClient>()
        TestSaksbehandler().init()
        OppgaveTestDataBuilder()
    }

    @Disabled("Automatiske behandlinger uten åpne aksjonspunkter kan dukke opp i køer")
    @Test
    fun `Behandling uten åpne aksjonspunkter skal ikke vises i køer`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val opprettetUtenÅpneAksjonspunkter = K9SakEventDtoBuilder(eksternId).opprettet().build()
        k9SakEventHandler.prosesser(opprettetUtenÅpneAksjonspunkter)

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val skjermet = runBlocking { pepClient.harTilgangTilKode6() }
        val filtrerReserverte = true
        val antallIKø = oppgaveKøTjeneste.hentAntallOppgaverForKø(
            oppgaveKoId = kø.id,
            filtrerReserverte = filtrerReserverte,
            skjermet = skjermet
        )
        assertThat(antallIKø).isEqualTo(0)
    }

    @Test
    fun `Behandlinger på vent skal ikke vises i køer, men skal kunne reserveres`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val opprettetUtenÅpneAksjonspunkter =
            K9SakEventDtoBuilder(eksternId).venterPåInntektsmelding().build()
        k9SakEventHandler.prosesser(opprettetUtenÅpneAksjonspunkter)

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val skjermet = runBlocking { pepClient.harTilgangTilKode6() }
        val filtrerReserverte = true
        val antallIKø = oppgaveKøTjeneste.hentAntallOppgaverForKø(
            oppgaveKoId = kø.id,
            filtrerReserverte = filtrerReserverte,
            skjermet = skjermet
        )
        assertThat(antallIKø).isEqualTo(0)

        val reservasjonTjeneste = get<OppgaveApisTjeneste>()
        val reservasjoner = runBlocking {
            reservasjonTjeneste.reserverOppgave(
                TestSaksbehandler.SARA,
                OppgaveIdMedOverstyringDto(
                    oppgaveNøkkel = TestOppgaveNøkkel.forK9sak(eksternId)
                )
            )
            reservasjonTjeneste.hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.SARA)
        }

        assertThat(reservasjoner.isNotEmpty())
    }


    @Test //Basert på prodfeil der k9sak sendte opprettet aksjonspunkt med frist og venteårsak null, som gjorde at konsument stoppet
    fun `Behandling med aksjonspunkt med ventekode NULL skal ikke kaste exception under mottak`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val opprettetAksjonspunktMedNullVenteårsak = K9SakEventDtoBuilder(eksternId).opprettet()
            .medAksjonspunkt(AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.builder()
                .medStatus(AksjonspunktStatus.OPPRETTET)
                .medVenteårsakOgFrist(null, frist = LocalDateTime.now().plusWeeks(1)))
            .build()

        k9SakEventHandler.prosesser(opprettetAksjonspunktMedNullVenteårsak)
    }

    @Test
    fun `Behandlinger på vent skal ikke kunne plukkes`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val opprettetUtenÅpneAksjonspunkter =
            K9SakEventDtoBuilder(eksternId).venterPåInntektsmelding().build()
        k9SakEventHandler.prosesser(opprettetUtenÅpneAksjonspunkter)

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val skjermet = runBlocking { pepClient.harTilgangTilKode6() }
        val filtrerReserverte = true
        val antallIKø = oppgaveKøTjeneste.hentAntallOppgaverForKø(
            oppgaveKoId = kø.id,
            filtrerReserverte = filtrerReserverte,
            skjermet = skjermet
        )
        assertThat(antallIKø).isEqualTo(0)

        val resultat = oppgaveKøTjeneste.taReservasjonFraKø(
            TestSaksbehandler.SARA.id!!,
            kø.id,
            CoroutineRequestContext(mockk<IIdToken>(relaxed = true))
        )
        assertThat(resultat is OppgaveMuligReservert.IkkeReservert).isTrue()
    }

    @Test
    fun `Åpne behandlinger skal kunne plukkes og fjernes fra kø`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        k9SakEventHandler.prosesser(K9SakEventDtoBuilder(eksternId).vurderSykdom().build())

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val skjermet = runBlocking { pepClient.harTilgangTilKode6() }
        val filtrerReserverte = true
        val antallIKø = oppgaveKøTjeneste.hentAntallOppgaverForKø(
            oppgaveKoId = kø.id,
            filtrerReserverte = filtrerReserverte,
            skjermet = skjermet
        )
        assertThat(antallIKø).isEqualTo(1)

        val resultat = taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertThat(resultat is OppgaveMuligReservert.Reservert).isTrue()

        val skjermet1 = runBlocking { pepClient.harTilgangTilKode6() }
        val filtrerReserverte1 = true
        val antallIKøEtterRes = oppgaveKøTjeneste.hentAntallOppgaverForKø(kø.id,
            filtrerReserverte = filtrerReserverte1,
            skjermet = skjermet1
        )
        assertThat(antallIKøEtterRes).isZero()
    }

    @Test
    fun `Reservasjoner skal ikke annulleres ved udefinert venteårsak på manuelt aksjonspunkt`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId))

        // Opprettet event
        val eventBuilder = K9SakEventDtoBuilder(eksternId)
        k9SakEventHandler.prosesser(eventBuilder.opprettet().build())

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val skjermet = runBlocking<Boolean> { pepClient.harTilgangTilKode6() }
        val filtrerReserverte = true
        val antallIKø = oppgaveKøTjeneste.hentAntallOppgaverForKø(
            oppgaveKoId = kø.id,
            filtrerReserverte = filtrerReserverte,
            skjermet = skjermet
        )
        assertThat(antallIKø).isEqualTo(1)

        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)

        // Transient tilstand mens k9-sak jobber
        k9SakEventHandler.prosesser(
            eventBuilder
                .medAksjonspunkt(
                    AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.builder()
                        .medVenteårsakOgFrist(Venteårsak.UDEFINERT, null)
                        .medStatus(AksjonspunktStatus.OPPRETTET)
                )
                .medBehandlingStatus(BehandlingStatus.UTREDES)
                .medBehandlingSteg(BehandlingStegType.INNHENT_REGISTEROPP)
                .build()
        )

        assertSkjultReservasjon(TestSaksbehandler.SARA)
        k9SakEventHandler.prosesser(eventBuilder.vurderSykdom().build())

        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)
    }

    @Test
    fun `Reservasjon skal annulleres hvis alle behandlinger i reservasjonen er avsluttet eller på vent`() {
        val eksternId1 = UUID.randomUUID()
        val eksternId2 = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(listOf(eksternId1, eksternId2), Oppgavestatus.AAPEN))

        val behandling1 = K9SakEventDtoBuilder(eksternId1, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        val behandling2 = K9SakEventDtoBuilder(eksternId2, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        k9SakEventHandler.prosesser(behandling1.vurderSykdom().build())
        k9SakEventHandler.prosesser(behandling2.vurderSykdom().build())

        // Saksbehandler tar reservasjon på begge sakene på pleietrengende
        assertAntallIKø(kø, 2)
        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertAntallIKø(kø, 0)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 2)

        // Saksbehandler sender den ene saken til beslutter, og beslutter reserverer oppgaven
        k9SakEventHandler.prosesser(behandling1.hosBeslutter().build())
        assertAntallIKø(kø, 1)
        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)
        assertAntallIKø(kø, 0)

        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Beslutter fatter vedtak
        k9SakEventHandler.prosesser(behandling1.avsluttet().build())
        assertAntallIKø(kø, 0)
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)

        // Saksbehandler setter den gjenstående oppgaven på vent og både saksbehandlers og den skjulte reservasjonen hos beslutter annulleres
        k9SakEventHandler.prosesser(behandling2.venterPåInntektsmelding().build())
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
    }


    @Test
    fun `Reservasjon skal skjules fra beslutter etter retur uavhengig av om det finnes åpne oppgaver som er hos saksbehandler`() {
        val eksternId1 = UUID.randomUUID()
        val eksternId2 = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(listOf(eksternId1, eksternId2), Oppgavestatus.AAPEN))

        val behandling1 = K9SakEventDtoBuilder(eksternId1, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        val behandling2 = K9SakEventDtoBuilder(eksternId2, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        k9SakEventHandler.prosesser(behandling1.vurderSykdom().build())
        k9SakEventHandler.prosesser(behandling2.vurderSykdom().build())

        // Saksbehandler tar reservasjon på begge sakene på pleietrengende
        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 2)
        // Saksbehandler setter den ene saken på vent og sender den andre saken til beslutter
        k9SakEventHandler.prosesser(behandling2.hosBeslutter().build())
        k9SakEventHandler.prosesser(behandling1.venterPåInntektsmelding().build())

        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)
        assertSkjultReservasjon(TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Beslutter sender i retur
        k9SakEventHandler.prosesser(behandling2.returFraBeslutter().build())
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
        k9SakEventHandler.prosesser(behandling2.opprettet().build())
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
    }

    @Test
    fun `Både saksbehandler og beslutters reservasjon skal annulleres hvis behandlinger settes på vent`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val eventBuilder = K9SakEventDtoBuilder(eksternId)

        // Åpen oppgave plukkes av saksbehandler
        k9SakEventHandler.prosesser(eventBuilder.vurderSykdom().build())
        assertAntallIKø(kø, 1)
        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertAntallIKø(kø, 0)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)

        // Behandling sendt til beslutter, beslutter plukken oppgaven
        k9SakEventHandler.prosesser(eventBuilder.hosBeslutter().build())
        assertAntallIKø(kø, 1)
        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)
        assertAntallIKø(kø, 0)

        assertSkjultReservasjon(TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Beslutter sender oppgaven tilbake til saksbehandler
        k9SakEventHandler.prosesser(eventBuilder.returFraBeslutter().build())
        assertAntallIKø(kø, 0)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        // Behandlingen settes på vent - begge reservasjonene annulleres
        k9SakEventHandler.prosesser(eventBuilder.manueltSattPåVentMedisinskeOpplysninger().build())
        assertAntallIKø(kø, 0)
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        // Oppgaven er gjenåpnet og saksbehandler plukker oppgaven på nytt
        k9SakEventHandler.prosesser(eventBuilder.vurderSykdom().build())
        assertAntallIKø(kø, 1)
        taReservasjonFra(kø, TestSaksbehandler.SARA)

        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
    }

    @Test
    fun `Retur fra beslutter skal ikke henge igjen`() {
        val eksternId1 = UUID.randomUUID()
        val eksternId2 = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(listOf(eksternId1, eksternId2), Oppgavestatus.AAPEN))

        val saksnummer = "SAKSNUMMER"
        val behandling1 = K9SakEventDtoBuilder(eksternId1, saksnummer = saksnummer,  pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        k9SakEventHandler.prosesser(behandling1.vurderSykdom().build())

        // Saksbehandler tar reservasjon på begge sakene på pleietrengende
        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)

        k9SakEventHandler.prosesser(behandling1.hosBeslutter().build())
        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)
        assertSkjultReservasjon(TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        val avsluttes = behandling1.beslutterGodkjent().build()
        val avsluttet = behandling1.avsluttet().build()
        k9SakEventHandler.prosesser(avsluttet)
        k9SakEventHandler.prosesser(avsluttes)     // Feil rekkefølge under iverksettelse av behandling i k9-sak

        runBlocking { get<OppgaveApisTjeneste>().hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.BIRGER_BESLUTTER) }
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
        assertIngenReservasjon(TestSaksbehandler.SARA)

        val behandling2 = K9SakEventDtoBuilder(eksternId2, saksnummer = saksnummer, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        k9SakEventHandler.prosesser(behandling2.vurderSykdom().build())

        // Saksbehandler tar reservasjon på begge sakene på pleietrengende
        taReservasjonFra(kø, TestSaksbehandler.SARA)

        k9SakEventHandler.prosesser(behandling2.hosBeslutter().build())
        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)

        k9SakEventHandler.prosesser(behandling2.returFraBeslutter().build())
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        k9SakEventHandler.prosesser(behandling2.foreslåVedtak().build())
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        k9SakEventHandler.prosesser(behandling2.hosBeslutter().build())
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Denne kan fjernes når historikkvask er endret til å kjøres automatisk ved behov i eventhandler
        K9SakTilLosHistorikkvaskTjeneste(get(),get(),get(),get(),get(),get()).vaskOppgaveForBehandlingUUID(eksternId1)

        k9SakEventHandler.prosesser(behandling2.avsluttet().build())
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
        assertIngenReservasjon(TestSaksbehandler.SARA)
    }


    private fun assertAntallIKø(kø: OppgaveKo, forventetAntall: Int) {
        oppgaveKøTjeneste.clearCache()
        val skjermet = runBlocking { pepClient.harTilgangTilKode6() }
        val filtrerReserverte = true
        val antallIKøEtterRes = oppgaveKøTjeneste.hentAntallOppgaverForKø(
            oppgaveKoId = kø.id,
            filtrerReserverte = filtrerReserverte,
            skjermet = skjermet
        )
        assertThat(antallIKøEtterRes).isEqualTo(forventetAntall.toLong())
    }

    private fun taReservasjonFra(kø: OppgaveKo, saksbehandler: Saksbehandler): OppgaveMuligReservert {
        return oppgaveKøTjeneste.taReservasjonFraKø(
            saksbehandler.id!!,
            kø.id,
            CoroutineRequestContext(mockk<IIdToken>(relaxed = true))
        )
    }

    private fun assertIngenReservasjon(saksbehandler: Saksbehandler) {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        runBlocking { assertThat(
            oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(saksbehandler)
        ).isEmpty() }
    }

    private fun assertSkjultReservasjon(saksbehandler: Saksbehandler) {
        assertReservasjon(saksbehandler, antallReservasjoner = 1, antallOppgaver = 0)
    }

    private fun assertReservasjonMedAntallOppgaver(saksbehandler: Saksbehandler, antallOppgaver: Int) {
        assertReservasjon(saksbehandler, antallReservasjoner = 1, antallOppgaver = antallOppgaver)
    }

    private fun assertReservasjon(saksbehandler: Saksbehandler, antallReservasjoner: Int, antallOppgaver: Int) {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        val reservasjon = runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(saksbehandler) }
        assertThat(reservasjon).hasSize(antallReservasjoner)
        reservasjon.firstOrNull()?.let {
            assertThat(it.reserverteV3Oppgaver.filter { it.oppgavestatus ==  Oppgavestatus.AAPEN}).hasSize(antallOppgaver)
        }
    }

    private fun opprettKøFor(saksbehandler: Saksbehandler, oppgaveQuery: OppgaveQuery): OppgaveKo {
        val oppgaveKoRepository = get<OppgaveKoRepository>()
        val pepClient = get<IPepClient>()
        val skjermet = runBlocking { pepClient.harTilgangTilKode6() }
        val nyKø = oppgaveKoRepository.leggTil("Test", skjermet = skjermet).copy(
            saksbehandlere = listOf(saksbehandler.epost),
            oppgaveQuery = oppgaveQuery
        )
        return oppgaveKoRepository.endre(nyKø, skjermet)
    }

    private fun querySomKunInneholder(eksternId: UUID, vararg status: Oppgavestatus = emptyArray()): OppgaveQuery {
        val filtre = mutableListOf(
            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, eksternId.toString())
        )
        if (status.isNotEmpty()) {
            filtre.add(
                byggFilter(
                    FeltType.OPPGAVE_STATUS,
                    EksternFeltverdiOperator.EQUALS,
                    *status.map { it.kode }.toTypedArray()
                )
            )
        }
        return OppgaveQuery(filtre)
    }

    private fun querySomKunInneholder(eksternId: List<UUID>, vararg status: Oppgavestatus = emptyArray()): OppgaveQuery {
        val filtre = mutableListOf(
            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.IN, *eksternId.map { it.toString() }.toTypedArray())
        )
        if (status.isNotEmpty()) {
            filtre.add(
                byggFilter(
                    FeltType.OPPGAVE_STATUS,
                    EksternFeltverdiOperator.EQUALS,
                    *status.map { it.kode }.toTypedArray()
                )
            )
        }
        return OppgaveQuery(filtre)
    }
}

object TestOppgaveNøkkel {
    fun forK9sak(eksternId: UUID) = OppgaveNøkkelDto(
        oppgaveEksternId = eksternId.toString(),
        oppgaveTypeEksternId = "k9sak",
        områdeEksternId = "K9",
    )
}

private fun byggFilter(feltType: FeltType, operator: EksternFeltverdiOperator, vararg verdier: String?): FeltverdiOppgavefilter {
    return FeltverdiOppgavefilter(
        feltType.område,
        feltType.eksternId,
        operator,
        verdier.toList()
    )
}
