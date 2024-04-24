package no.nav.k9.los.nyoppgavestyring.k9saktillosadapter

import assertk.assertThat
import assertk.assertions.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.*
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.integrasjon.rest.CoroutineRequestContext
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.query.db.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.tjenester.saksbehandler.IIdToken
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveApisTjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveIdMedOverstyringDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.get
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
        val antallIDb = oppgaveQueryService.queryForAntall(querySomKunInneholder(eksternId))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id)
        assertThat(antallIKø).isEqualTo(0)
    }

    @Test
    fun `Behandlinger på vent skal ikke vises i køer, men skal kunne reserveres`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val opprettetUtenÅpneAksjonspunkter = BehandlingProsessEventDtoBuilder(eksternId).venterPåInntektsmelding().build()
        eventHandler.prosesser(opprettetUtenÅpneAksjonspunkter)

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(querySomKunInneholder(eksternId))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id)
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

    @Test
    fun `Behandlinger på vent skal ikke kunne plukkes`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        val opprettetUtenÅpneAksjonspunkter = BehandlingProsessEventDtoBuilder(eksternId).venterPåInntektsmelding().build()
        eventHandler.prosesser(opprettetUtenÅpneAksjonspunkter)

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(querySomKunInneholder(eksternId))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id)
        assertThat(antallIKø).isEqualTo(0)

        val resultat = oppgaveKøTjeneste.taReservasjonFraKø(
            TestSaksbehandler.SARA.id!!,
            kø.id,
            CoroutineRequestContext(mockk<IIdToken>(relaxed = true))
        )
        assertThat(resultat).isNull()
    }

    @Test
    fun `Åpne behandlinger skal kunne plukkes og fjernes fra kø`() {
        val eksternId = UUID.randomUUID()
        val kø = opprettKøFor(TestSaksbehandler.SARA, querySomKunInneholder(eksternId, Oppgavestatus.AAPEN))

        eventHandler.prosesser(BehandlingProsessEventDtoBuilder(eksternId).vurderSykdom().build())

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(querySomKunInneholder(eksternId))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id)
        assertThat(antallIKø).isEqualTo(1)

        val resultat = taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertThat(resultat).isNotNull()

        val antallIKøEtterRes = oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id)
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
        val antallIDb = oppgaveQueryService.queryForAntall(querySomKunInneholder(eksternId))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id)
        assertThat(antallIKø).isEqualTo(1)

        taReservasjonFra(kø, TestSaksbehandler.SARA)
        assertReservasjonMedAntallOppgaver(TestSaksbehandler.SARA, 1)

        eventHandler.prosesser(eventBuilder
            .medAksjonspunkt(AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.builder()
                .medVenteårsakOgFrist(Venteårsak.UDEFINERT, null)
                .medStatus(AksjonspunktStatus.OPPRETTET))
            .medBehandlingStatus(BehandlingStatus.UTREDES)
            .medBehandlingSteg(BehandlingStegType.INNHENT_REGISTEROPP)
        .build())

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
        eventHandler.prosesser(eventBuilder.venterPåInntektsmelding().build())
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

    private fun assertAntallIKø(kø: OppgaveKo, forventetAntall: Int) {
        val antallIKøEtterRes = oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id)
        assertThat(antallIKøEtterRes).isEqualTo(forventetAntall.toLong())
    }

    private fun taReservasjonFra(kø: OppgaveKo, saksbehandler: Saksbehandler) {
        oppgaveKøTjeneste.taReservasjonFraKø(
            saksbehandler.id!!,
            kø.id,
            CoroutineRequestContext(mockk<IIdToken>(relaxed = true))
        )
    }

    private fun assertIngenReservasjon(saksbehandler: Saksbehandler) {
        assertReservasjon(saksbehandler, antallReservasjoner = 0, antallOppgaver = 0)
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
            assertThat(it.reserverteV3Oppgaver).hasSize(antallOppgaver)
        }
    }


    private fun opprettKøFor(saksbehandler: Saksbehandler, oppgaveQuery: OppgaveQuery): OppgaveKo {
        val oppgaveKoRepository = get<OppgaveKoRepository>()
        val nyKø = oppgaveKoRepository.leggTil("Test").copy(
            saksbehandlere = listOf(saksbehandler.epost),
            oppgaveQuery = oppgaveQuery
        )
        return oppgaveKoRepository.endre(nyKø)
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