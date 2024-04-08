package no.nav.k9.los.nyoppgavestyring.k9saktillosadapter

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isZero
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.BehandlingProsessEventDtoBuilder
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.aksjonspunktbehandling.TestSaksbehandler
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

        val opprettetUtenÅpneAksjonspunkter = BehandlingProsessEventDtoBuilder.opprettet(eksternId).build()
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

        val opprettetUtenÅpneAksjonspunkter = BehandlingProsessEventDtoBuilder.venterPåInntektsmelding(eksternId).build()
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

        val opprettetUtenÅpneAksjonspunkter = BehandlingProsessEventDtoBuilder.venterPåInntektsmelding(eksternId).build()
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

        val opprettetSykdomsvurdering = BehandlingProsessEventDtoBuilder.vurderSykdom(eksternId).build()
        eventHandler.prosesser(opprettetSykdomsvurdering)

        val oppgaveQueryService = get<OppgaveQueryService>()
        val antallIDb = oppgaveQueryService.queryForAntall(querySomKunInneholder(eksternId))
        assertThat(antallIDb).isEqualTo(1)

        val antallIKø = oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id)
        assertThat(antallIKø).isEqualTo(1)

        val resultat = oppgaveKøTjeneste.taReservasjonFraKø(
            TestSaksbehandler.SARA.id!!,
            kø.id,
            CoroutineRequestContext(mockk<IIdToken>(relaxed = true))
        )
        assertThat(resultat).isNotNull()

        val antallIKøEtterRes = oppgaveKøTjeneste.hentAntallUreserverteOppgaveForKø(kø.id)
        assertThat(antallIKøEtterRes).isZero()
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