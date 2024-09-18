package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9saktillos.k9saktillosadapter

import assertk.assertThat
import assertk.assertions.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.*
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.integrasjon.rest.CoroutineRequestContext
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Dto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.tjenester.saksbehandler.IIdToken
import no.nav.k9.los.tjenester.saksbehandler.oppgave.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


class K9SakTilLosIT : AbstractK9LosIntegrationTest() {

    lateinit var eventHandler: K9sakEventHandler
    lateinit var oppgaveKøTjeneste: OppgaveKoTjeneste

    @BeforeEach
    fun setup() {
        eventHandler = get<K9sakEventHandler>()
        oppgaveKøTjeneste = get<OppgaveKoTjeneste>()
        TestSaksbehandler().init()
        OppgaveTestDataBuilder()
    }

    @Disabled("Automatiske behandlinger uten åpne aksjonspunkter kan dukke opp i køer")
    @Test
    fun `Behandling uten åpne aksjonspunkter skal ikke vises i køer`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val opprettetUtenÅpneAksjonspunkter = BehandlingProsessEventDtoBuilder(eksternId).opprettet().build()
        eventHandler.prosesser(opprettetUtenÅpneAksjonspunkter)

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = runBlocking { oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id) }
        assertThat(antallIKø).isEqualTo(0)
    }

    @Test
    fun `Behandlinger på vent skal ikke vises i køer, men skal kunne reserveres`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val opprettetUtenÅpneAksjonspunkter = BehandlingProsessEventDtoBuilder(eksternId).venterPåInntektsmelding().build()
        eventHandler.prosesser(opprettetUtenÅpneAksjonspunkter)

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = runBlocking { oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id) }
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

        val opprettetAksjonspunktMedNullVenteårsak = BehandlingProsessEventDtoBuilder(eksternId).opprettet()
            .medAksjonspunkt(AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.builder()
                .medStatus(AksjonspunktStatus.OPPRETTET)
                .medVenteårsakOgFrist(null, frist = LocalDateTime.now().plusWeeks(1)))
            .build()

        eventHandler.prosesser(opprettetAksjonspunktMedNullVenteårsak)
    }

    @Test
    fun `Behandlinger på vent skal ikke kunne plukkes`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val opprettetUtenÅpneAksjonspunkter = BehandlingProsessEventDtoBuilder(eksternId).venterPåInntektsmelding().build()
        eventHandler.prosesser(opprettetUtenÅpneAksjonspunkter)

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = runBlocking { oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id) }
        assertThat(antallIKø).isEqualTo(0)

        val resultat = runBlocking { oppgaveKøTjeneste.taReservasjonFraKø(
            TestSaksbehandler.SARA.id!!,
            kø.id,
            CoroutineRequestContext(mockk<IIdToken>(relaxed = true))
        )
            }
        assertThat(resultat).isNull()
    }

    @Test
    fun `Åpne behandlinger skal kunne plukkes og fjernes fra kø`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        eventHandler.prosesser(BehandlingProsessEventDtoBuilder(eksternId).vurderSykdom().build())

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = runBlocking { oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id) }
        assertThat(antallIKø).isEqualTo(1)

        val resultat = taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertThat(resultat).isNotNull()

        val antallIKøEtterRes = runBlocking { oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id) }
        assertThat(antallIKøEtterRes).isZero()
    }

    @Test
    fun `Reservasjoner skal ikke annulleres ved udefinert venteårsak på manuelt aksjonspunkt`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId))

        // Opprettet event
        val eventBuilder = BehandlingProsessEventDtoBuilder(eksternId)
        eventHandler.prosesser(eventBuilder.opprettet().build())

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(QueryRequest(querySomKunInneholder(eksternId)))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = runBlocking { oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id) }
        assertThat(antallIKø).isEqualTo(1)

        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)

        // Transient tilstand mens k9-sak jobber
        eventHandler.prosesser(eventBuilder
            .medAksjonspunkt(AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.builder()
                .medVenteårsakOgFrist(Venteårsak.UDEFINERT, null)
                .medStatus(AksjonspunktStatus.OPPRETTET))
            .medBehandlingStatus(BehandlingStatus.UTREDES)
            .medBehandlingSteg(BehandlingStegType.INNHENT_REGISTEROPP)
        .build())

        assertSkjultReservasjon(TestSaksbehandler.SARA)
        eventHandler.prosesser(eventBuilder.vurderSykdom().build())

        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)
    }

    @Test
    fun `Reservasjon skal annulleres hvis alle behandlinger i reservasjonen er avsluttet eller på vent`() {
        val eksternId1 = UUID.randomUUID()
        val eksternId2 = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(listOf(eksternId1, eksternId2), Oppgavestatus.AAPEN))

        val behandling1 = BehandlingProsessEventDtoBuilder(eksternId1, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        val behandling2 = BehandlingProsessEventDtoBuilder(eksternId2, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        eventHandler.prosesser(behandling1.vurderSykdom().build())
        eventHandler.prosesser(behandling2.vurderSykdom().build())

        // Saksbehandler tar reservasjon på begge sakene på pleietrengende
        assertAntallIKø(kø, 2)
        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertAntallIKø(kø, 0)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 2)

        // Saksbehandler sender den ene saken til beslutter, og beslutter reserverer oppgaven
        eventHandler.prosesser(behandling1.hosBeslutter().build())
        assertAntallIKø(kø, 1)
        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)
        assertAntallIKø(kø, 0)

        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Beslutter fatter vedtak
        eventHandler.prosesser(behandling1.avsluttet().build())
        assertAntallIKø(kø, 0)
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)

        // Saksbehandler setter den gjenstående oppgaven på vent og både saksbehandlers og den skjulte reservasjonen hos beslutter annulleres
        eventHandler.prosesser(behandling2.venterPåInntektsmelding().build())
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
    }


    @Test
    fun `Reservasjon skal skjules fra beslutter etter retur uavhengig av om det finnes åpne oppgaver som er hos saksbehandler`() {
        val eksternId1 = UUID.randomUUID()
        val eksternId2 = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(listOf(eksternId1, eksternId2), Oppgavestatus.AAPEN))

        val behandling1 = BehandlingProsessEventDtoBuilder(eksternId1, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        val behandling2 = BehandlingProsessEventDtoBuilder(eksternId2, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        eventHandler.prosesser(behandling1.vurderSykdom().build())
        eventHandler.prosesser(behandling2.vurderSykdom().build())

        // Saksbehandler tar reservasjon på begge sakene på pleietrengende
        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 2)
        // Saksbehandler setter den ene saken på vent og sender den andre saken til beslutter
        eventHandler.prosesser(behandling2.hosBeslutter().build())
        eventHandler.prosesser(behandling1.venterPåInntektsmelding().build())

        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)
        assertSkjultReservasjon(TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Beslutter sender i retur
        eventHandler.prosesser(behandling2.returFraBeslutter().build())
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
        eventHandler.prosesser(behandling2.opprettet().build())
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
    }

    @Test
    fun `Både saksbehandler og beslutters reservasjon skal annulleres hvis behandlinger settes på vent`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val eventBuilder = BehandlingProsessEventDtoBuilder(eksternId)

        // Åpen oppgave plukkes av saksbehandler
        eventHandler.prosesser(eventBuilder.vurderSykdom().build())
        assertAntallIKø(kø, 1)
        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertAntallIKø(kø, 0)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)

        // Behandling sendt til beslutter, beslutter plukken oppgaven
        eventHandler.prosesser(eventBuilder.hosBeslutter().build())
        assertAntallIKø(kø, 1)
        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)
        assertAntallIKø(kø, 0)

        assertSkjultReservasjon(TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Beslutter sender oppgaven tilbake til saksbehandler
        eventHandler.prosesser(eventBuilder.returFraBeslutter().build())
        assertAntallIKø(kø, 0)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        // Behandlingen settes på vent - begge reservasjonene annulleres
        eventHandler.prosesser(eventBuilder.manueltSattPåVentMedisinskeOpplysninger().build())
        assertAntallIKø(kø, 0)
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        // Oppgaven er gjenåpnet og saksbehandler plukker oppgaven på nytt
        eventHandler.prosesser(eventBuilder.vurderSykdom().build())
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
        val behandling1 = BehandlingProsessEventDtoBuilder(eksternId1, saksnummer = saksnummer,  pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        eventHandler.prosesser(behandling1.vurderSykdom().build())

        // Saksbehandler tar reservasjon på begge sakene på pleietrengende
        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)

        eventHandler.prosesser(behandling1.hosBeslutter().build())
        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)
        assertSkjultReservasjon(TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        val avsluttes = behandling1.beslutterGodkjent().build()
        val avsluttet = behandling1.avsluttet().build()
        eventHandler.prosesser(avsluttet)
        eventHandler.prosesser(avsluttes)     // Feil rekkefølge under iverksettelse av behandling i k9-sak

        runBlocking { get<OppgaveApisTjeneste>().hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.BIRGER_BESLUTTER) }
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
        assertIngenReservasjon(TestSaksbehandler.SARA)

        val behandling2 = BehandlingProsessEventDtoBuilder(eksternId2, saksnummer = saksnummer, pleietrengendeAktørId = "PLEIETRENGENDE_ID")
        eventHandler.prosesser(behandling2.vurderSykdom().build())

        // Saksbehandler tar reservasjon på begge sakene på pleietrengende
        taReservasjonFra(kø, TestSaksbehandler.SARA)

        eventHandler.prosesser(behandling2.hosBeslutter().build())
        taReservasjonFra(kø, TestSaksbehandler.BIRGER_BESLUTTER)

        eventHandler.prosesser(behandling2.returFraBeslutter().build())
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        eventHandler.prosesser(behandling2.foreslåVedtak().build())
        assertSkjultReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        eventHandler.prosesser(behandling2.hosBeslutter().build())
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Denne kan fjernes når historikkvask er endret til å kjøres automatisk ved behov i eventhandler
        K9SakTilLosHistorikkvaskTjeneste(get(),get(),get(),get(),get(),get()).vaskOppgaveForBehandlingUUID(eksternId1)

        eventHandler.prosesser(behandling2.avsluttet().build())
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
        assertIngenReservasjon(TestSaksbehandler.SARA)
    }


    private fun assertAntallIKø(kø: OppgaveKo, forventetAntall: Int) {
        val antallIKøEtterRes = runBlocking { oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id) }
        assertThat(antallIKøEtterRes).isEqualTo(forventetAntall.toLong())
    }

    private fun taReservasjonFra(kø: OppgaveKo, saksbehandler: Saksbehandler): Pair<Oppgave, ReservasjonV3>? {
        return runBlocking {
            oppgaveKøTjeneste.taReservasjonFraKø(
                saksbehandler.id!!,
                kø.id,
                CoroutineRequestContext(mockk<IIdToken>(relaxed = true))
            )
        }
    }

    private fun hentEnesteReservasjon(saksbehandler: Saksbehandler): ReservasjonV3Dto {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        return runBlocking {
            oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(saksbehandler).also {
                assertThat(it).hasSize(1)
            }.first()
        }
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

    private fun leggTilbakeAlleReservasjoner(saksbehandler: Saksbehandler) {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        runBlocking {
            oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(saksbehandler).forEach { reservasjon ->

                val params =
                    listOf(AnnullerReservasjon(reservasjon.reservertOppgaveV1Dto?.oppgaveNøkkel
                        ?: reservasjon.reserverteV3Oppgaver.first().oppgaveNøkkel))

                oppgaveApisTjeneste.annullerReservasjoner(params, saksbehandler)
            }
        }
    }

    private fun forleng(saksbehandler: Saksbehandler, reservasjon: ReservasjonV3Dto, tilDato: LocalDateTime) {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        val nøkler = listOfNotNull(reservasjon.reservertOppgaveV1Dto?.oppgaveNøkkel).takeIf { it.isNotEmpty() }
            ?: reservasjon.reserverteV3Oppgaver.map { it.oppgaveNøkkel }

        runBlocking {
            nøkler.forEach {
                oppgaveApisTjeneste.forlengReservasjon(
                    ForlengReservasjonDto(
                        it,
                        "begrunnelse",
                        tilDato
                    ), saksbehandler
                )
            }
        }
    }


    private fun endre(saksbehandler: Saksbehandler, nøkler: List<OppgaveNøkkelDto>, tilDato: LocalDate) {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()

        runBlocking {
            oppgaveApisTjeneste.endreReservasjoner(
                nøkler.map { ReservasjonEndringDto(
                    it,
                    saksbehandler.brukerIdent,
                    tilDato,
                    "begrunnelse",
                )}, saksbehandler
            )
        }
    }

    private fun ReservasjonV3Dto.oppgaveNøkkelDtos() =
        (listOfNotNull(reservertOppgaveV1Dto?.oppgaveNøkkel).takeIf { it.isNotEmpty() }
            ?: reserverteV3Oppgaver.map { it.oppgaveNøkkel })


    private fun overfør(saksbehandler: Saksbehandler, nøkler: List<OppgaveNøkkelDto>) {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()

        runBlocking {
            nøkler.forEach {
                oppgaveApisTjeneste.overførReservasjon(
                    FlyttReservasjonId(
                        it,
                        saksbehandler.brukerIdent!!,
                        "begrunnelse",
                    ), saksbehandler
                )
            }
        }
    }



    private fun opprettKøFor(saksbehandler: Saksbehandler, oppgaveQuery: OppgaveQuery): OppgaveKo {
        val oppgaveKoRepository = get<OppgaveKoRepository>()
        val nyKø = oppgaveKoRepository.leggTil("Test", kode6 = false).copy(
            saksbehandlere = listOf(saksbehandler.epost),
            oppgaveQuery = oppgaveQuery
        )
        return oppgaveKoRepository.endre(nyKø, false)
    }

    private fun querySomKunInneholder(eksternId: UUID, vararg status: Oppgavestatus = emptyArray()): OppgaveQuery {
        val filtre = mutableListOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, eksternId.toString())
        )
        if (status.isNotEmpty()) {
            filtre.add(byggGenereltFilter(FeltType.OPPGAVE_STATUS, FeltverdiOperator.EQUALS, *status.map { it.kode }.toTypedArray()))
        }
        return OppgaveQuery(filtre)
    }

    private fun querySomKunInneholder(eksternId: List<UUID>, vararg status: Oppgavestatus = emptyArray()): OppgaveQuery {
        val filtre = mutableListOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.IN, *eksternId.map { it.toString() }.toTypedArray())
        )
        if (status.isNotEmpty()) {
            filtre.add(byggGenereltFilter(FeltType.OPPGAVE_STATUS, FeltverdiOperator.EQUALS, *status.map { it.kode }.toTypedArray()))
        }
        return OppgaveQuery(filtre)
    }

    fun hentOppgaver(eksternId: UUID): Map<Triple<Oppgavestatus, String, LocalDateTime>, Boolean> {
        return get<TransactionalManager>().transaction {  tx ->
            tx.run(
                queryOf(
                    "select * from oppgave_v3 where ekstern_id = :eksternId", mapOf("eksternId" to eksternId.toString())
                ).map { row ->
                    Triple(
                        Oppgavestatus.valueOf(row.string("status")),
                        row.string("reservasjonsnokkel"),
                        row.localDateTime("endret_tidspunkt"),
                    ) to row.boolean("aktiv")
                }.asList
            ).toMap()
        }
    }

}

object TestOppgaveNøkkel {
    fun forK9sak(eksternId: UUID) = OppgaveNøkkelDto(
        oppgaveEksternId = eksternId.toString(),
        oppgaveTypeEksternId = "k9sak",
        områdeEksternId = "K9",
    )
}

private fun byggFilterK9(feltType: FeltType, feltverdiOperator: FeltverdiOperator, vararg verdier: String?): FeltverdiOppgavefilter {
    return FeltverdiOppgavefilter(
        "K9",
        feltType.eksternId,
        feltverdiOperator.name,
        verdier.toList()
    )
}

private fun byggGenereltFilter(feltType: FeltType, feltverdiOperator: FeltverdiOperator, vararg verdier: String?): FeltverdiOppgavefilter {
    return FeltverdiOppgavefilter(
        null,
        feltType.eksternId,
        feltverdiOperator.name,
        verdier.toList()
    )
}