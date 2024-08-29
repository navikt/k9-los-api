package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9tilbaketillos.k9tilbaketillosadapter

import assertk.assertThat
import assertk.assertions.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.*
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import no.nav.k9.los.nyoppgavestyring.ko.dto.OppgaveKo
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Dto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.tjenester.saksbehandler.oppgave.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


class K9TilbakeTilLosIT : AbstractK9LosIntegrationTest() {

    lateinit var eventHandler: K9TilbakeEventHandler
    lateinit var oppgaveKøTjeneste: OppgaveKoTjeneste

    lateinit var oppgaveApisTjeneste: OppgaveApisTjeneste

    @BeforeEach
    fun setup() {
        eventHandler = get<K9TilbakeEventHandler>()
        oppgaveKøTjeneste = get<OppgaveKoTjeneste>()
        oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        TestSaksbehandler().init()
        OppgaveTestDataBuilder()
    }

    @Test
    fun `Både saksbehandler og beslutters har hver sin reservasjon og saken kan bli sendt i retur`() {
        val eksternId = UUID.randomUUID()
        val eventBuilder = BehandlingProsessEventTilbakeDtoBuilder(eksternId)

        // Åpen oppgave plukkes av saksbehandler
        eventHandler.prosesser(eventBuilder.opprettet().build())
        taReservasjon(TestSaksbehandler.SARA, eksternId)
        assertReservasjon(TestSaksbehandler.SARA, 1)

        eventHandler.prosesser(eventBuilder.foreslåVedtak().build())
        assertReservasjon(TestSaksbehandler.SARA, 1)

        // Behandling sendt til beslutter, beslutter plukken oppgaven
        eventHandler.prosesser(eventBuilder.hosBeslutter().build())
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        taReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, eksternId)
        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Beslutter sender oppgaven tilbake til saksbehandler
        eventHandler.prosesser(eventBuilder.returFraBeslutter().build())
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        taReservasjon(TestSaksbehandler.SARA, eksternId)
        assertReservasjon(TestSaksbehandler.SARA, 1)

        eventHandler.prosesser(eventBuilder.foreslåVedtak().build())
        assertReservasjon(TestSaksbehandler.SARA, 1)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        // Behandlingen sendes til ny beslutning
        eventHandler.prosesser(eventBuilder.hosBeslutter().build())
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)

        taReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, eksternId)
        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 1)

        // Oppgaven er avsluttet, begge reservasjonen skal annulleres
        eventHandler.prosesser(eventBuilder.avsluttet().build())
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertIngenReservasjon(TestSaksbehandler.BIRGER_BESLUTTER)
    }

    @Test
    fun `Tilbakereservasjoner påvirkes ikke av punsj`() {
        val eksternId = UUID.randomUUID()
        val aktørId = "123456789"
        val eventBuilder = BehandlingProsessEventTilbakeDtoBuilder(
            eksternId = eksternId,
            aktørId = aktørId,
            ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN
        )

        // Åpen oppgave plukkes av saksbehandler
        eventHandler.prosesser(eventBuilder.opprettet().build())
        taReservasjon(TestSaksbehandler.SARA, eksternId)
        assertReservasjon(TestSaksbehandler.SARA, 1)
        eventHandler.prosesser(eventBuilder.foreslåVedtak().build())

        // Innkommende papirsøknad på samme aktør som har tilbakekrevingssak
        val punsjId1 = UUID.randomUUID()
        val punsjEventHandler = get<K9punsjEventHandler>()
        val punsjEventDtoBuilder = PunsjEventDtoBuilder(eksternId = punsjId1, aktørId = aktørId, ytelse = FagsakYtelseType.PLEIEPENGER_SYKT_BARN)
        punsjEventHandler.prosesser(punsjEventDtoBuilder.papirsøknad().build())

        assertReservasjon(TestSaksbehandler.SARA, 1, Fagsystem.K9TILBAKE)

        // Behandling sendt til beslutter og beslutter plukker oppgave
        eventHandler.prosesser(eventBuilder.hosBeslutter().build())
        taReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, eksternId)

        // Innkommende punsjoppgave på samme aktørId
        val punsjId2 = UUID.randomUUID()
        punsjEventHandler.prosesser(punsjEventDtoBuilder.medEksternId(punsjId2).papirsøknad().build())

        // Saksbehandler har ikke fått noen ny reservasjon, beslutter har kun sin tilbake-beslutteroppgave
        assertIngenReservasjon(TestSaksbehandler.SARA)
        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 1, Fagsystem.K9TILBAKE)

        // Saksbehandler tar punsj reservasjon. Skal ikke påvirke tilbakeoppgave hos beslutter
        taReservasjon(TestSaksbehandler.SARA, punsjId1)
        assertReservasjon(TestSaksbehandler.SARA, 1, Fagsystem.PUNSJ)
        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 1, Fagsystem.K9TILBAKE)

        // Birger tar den andre punsjoppgaven
        taReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, punsjId2)
        val birgersReservasjoner = runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.BIRGER_BESLUTTER) }
        assertThat(birgersReservasjoner
            .mapNotNull { it.reservertOppgaveV1Dto?.system }
        ).containsExactlyInAnyOrder(Fagsystem.K9TILBAKE.kode, Fagsystem.PUNSJ.kode)

        // Tilbake sendes i retur, bare punsjreservasjonen igjen
        eventHandler.prosesser(eventBuilder.returFraBeslutter().build())
        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 1, Fagsystem.PUNSJ)
        assertReservasjon(TestSaksbehandler.SARA, 1, Fagsystem.PUNSJ)

        // Tar beslutter reservasjonen igjen på tilbake
        eventHandler.prosesser(eventBuilder.hosBeslutter().build())
        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 1, Fagsystem.PUNSJ)
        taReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, eksternId)

        // Punsjer ferdig og har bare tilbake-oppgave igjen
        punsjEventHandler.prosesser(punsjEventDtoBuilder.sendtInn(TestSaksbehandler.BIRGER_BESLUTTER).build())
//        assertReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, 1, Fagsystem.K9TILBAKE)
    }

    @Test
    fun `Tilbakereservasjoner påvirkes ikke av k9sak`() {
        val tilbakeEksternId = UUID.randomUUID()
        val aktørId = "123456789"
        val tilbakeEventBuilder = BehandlingProsessEventTilbakeDtoBuilder(
            eksternId = tilbakeEksternId,
            aktørId = aktørId,
            ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN
        )

        // Åpen oppgave plukkes av saksbehandler og sendes til beslutter
        eventHandler.prosesser(tilbakeEventBuilder.opprettet().build())
        taReservasjon(TestSaksbehandler.SARA, tilbakeEksternId)
        assertReservasjon(TestSaksbehandler.SARA, 1)
        eventHandler.prosesser(tilbakeEventBuilder.foreslåVedtak().build())
        eventHandler.prosesser(tilbakeEventBuilder.hosBeslutter().build())

        // Ny behandling i k9sak
        val k9sakEksternId = UUID.randomUUID()
        val k9sakEventBuilder = BehandlingProsessEventDtoBuilder(
            eksternId = k9sakEksternId,
            aktørId = aktørId,
            ytelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN
        )
        val k9sakEventHandler = get<K9sakEventHandler>()

        // Saksbehandler behandler k9sak
        k9sakEventHandler.prosesser(k9sakEventBuilder.vurderSykdom().build())
        taReservasjon(TestSaksbehandler.SARA, k9sakEksternId)
        runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.SARA) }.let { reservasjoner ->
            assertThat(reservasjoner).hasSize(1)
            assertThat(reservasjoner.first().reservertOppgaveV1Dto).isNull()
            assertThat(reservasjoner.first().reserverteV3Oppgaver.first().oppgaveNøkkel.oppgaveEksternId).isEqualTo(k9sakEksternId.toString())
        }

        // Beslutter tar k9sak-oppgave (uten at det påvirker ureserverte tilbake-oppgaven)
        k9sakEventHandler.prosesser(k9sakEventBuilder.hosBeslutter().build())
        taReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, k9sakEksternId)
        runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.BIRGER_BESLUTTER) }.let { reservasjoner ->
            assertThat(reservasjoner).hasSize(1)
            assertThat(reservasjoner.first().reservertOppgaveV1Dto).isNull()
            assertThat(reservasjoner.first().reserverteV3Oppgaver.first().oppgaveNøkkel.oppgaveEksternId).isEqualTo(k9sakEksternId.toString())
        }

        // Beslutter sender k9sak-oppgave i retur og har en skjult v3-reservasjon
        k9sakEventHandler.prosesser(k9sakEventBuilder.returFraBeslutter().build())
        runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.BIRGER_BESLUTTER) }.let { reservasjoner ->
            assertThat(reservasjoner).hasSize(1)
            assertThat(reservasjoner.first().reservertOppgaveV1Dto).isNull()
            assertThat(reservasjoner.first().reserverteV3Oppgaver).isEmpty()
        }

        // Beslutter tar tilbake-oppgave og sender i retur
        taReservasjon(TestSaksbehandler.BIRGER_BESLUTTER, tilbakeEksternId)
        runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.BIRGER_BESLUTTER) }.let { reservasjoner ->
            assertThat(reservasjoner).hasSize(2)
            assertThat(reservasjoner.first { it.reservertOppgaveV1Dto != null }.reservertOppgaveV1Dto?.system).assertThat(Fagsystem.K9TILBAKE.kode)
            assertThat(reservasjoner.flatMap { it.reserverteV3Oppgaver}).isEmpty()
        }

        eventHandler.prosesser(tilbakeEventBuilder.returFraBeslutter().build())
        runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.BIRGER_BESLUTTER) }.let { reservasjoner ->
            assertThat(reservasjoner).hasSize(1)
            assertThat(reservasjoner.first().reservertOppgaveV1Dto).isNull()
            assertThat(reservasjoner.first().reserverteV3Oppgaver).isEmpty()
        }

        // Både k9sak og tilbake klare hos beslutter med eksisterende k9-sak reservasjon og annullert tilbake
        k9sakEventHandler.prosesser(k9sakEventBuilder.hosBeslutter().build())
        eventHandler.prosesser(tilbakeEventBuilder.hosBeslutter().build())

        runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(TestSaksbehandler.BIRGER_BESLUTTER) }.let { reservasjoner ->
            assertThat(reservasjoner).hasSize(1)
            assertThat(reservasjoner.first().reservertOppgaveV1Dto).isNull()
            assertThat(reservasjoner.first().reserverteV3Oppgaver.first().oppgaveNøkkel.oppgaveEksternId).isEqualTo(k9sakEksternId.toString())
        }
    }

    private fun taReservasjon(saksbehandler: Saksbehandler, eksternId: UUID) {
        runBlocking {
            get<OppgaveApisTjeneste>().reserverOppgave(
                saksbehandler, OppgaveIdMedOverstyringDto(
                    OppgaveNøkkelDto.forV1Oppgave(eksternId.toString())
                )
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

    private fun assertReservasjon(saksbehandler: Saksbehandler, antallReservasjoner: Int, system: Fagsystem = Fagsystem.K9TILBAKE) {
        val oppgaveApisTjeneste = get<OppgaveApisTjeneste>()
        val reservasjon = runBlocking { oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(saksbehandler) }
        assertThat(reservasjon).hasSize(antallReservasjoner)
        reservasjon.firstOrNull()?.let {
            assertThat(it.reservertOppgaveV1Dto).isNotNull()
            assertThat(it.reservertOppgaveV1Dto?.system).isEqualTo(system.kode)
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
        val nyKø = oppgaveKoRepository.leggTil("Test", skjermet = false).copy(
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
