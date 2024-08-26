package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotlinx.coroutines.runBlocking
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class ReservasjonOversetterTest : AbstractK9LosIntegrationTest(){

    @BeforeEach
    fun setup() {
        val områdeSetup = get<OmrådeSetup>()
        områdeSetup.setup()
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        k9SakTilLosAdapterTjeneste.setup()
    }

    @Test
    fun taNyReservasjonFraGammelKontekstK9Sak() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "Z123459",
                    navn = "Burger Besliter",
                    epost = "burgerBesliter@nav.no",
                    enhet = "NAV DRIFT",
                )
            )
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "Z123457",
                    navn = "Beslutter Birger",
                    epost = "BirgerBeslutter@nav.no",
                    enhet = "NAV DRIFT",
                )
            )
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "Z123456",
                    navn = "Saksbehandler Sara",
                    epost = "saksbehandler@nav.no",
                    enhet = "NAV DRIFT",
                )
            )
        }

        val k9sakEventHandler = get<K9sakEventHandler>()

        val behandling1 = mockEvent("1234", aktørId = "1234567890", behandlingId = 123L, "11111111111")
        val behandling2 = mockEvent("1235", aktørId = "1234567891", behandlingId = 123L, "11111111111")

        k9sakEventHandler.prosesser(behandling1)
        k9sakEventHandler.prosesser(behandling2)

        val reservasjonV1Tjeneste = get<OppgaveTjeneste>()
        val oppgavestatus = runBlocking {
            reservasjonV1Tjeneste.reserverOppgave(
                "Z123456",
                overstyrIdent = null,
                oppgaveUuid = behandling1.eksternId!!,
                overstyrSjekk = false,
                overstyrBegrunnelse = null
            )
        }

        assertTrue(oppgavestatus.erReservert)
        assertEquals("Saksbehandler Sara", oppgavestatus.reservertAvNavn)

        val reservasjonKonverteringJobb = get<ReservasjonKonverteringJobb>()
        reservasjonKonverteringJobb.spillAvReservasjoner()

        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()
        val alleAktiveReservasjoner = reservasjonV3Tjeneste.hentAlleAktiveReservasjoner()

        val reservasjonV3MedOppgaver = alleAktiveReservasjoner.get(0)

        val saksbehandlerReservertV1 = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(oppgavestatus.reservertAv!!)!!
        }
        assertEquals(saksbehandlerReservertV1.id, reservasjonV3MedOppgaver.reservasjonV3.reservertAv)
    }

    fun mockEvent(saksnummer: String, aktørId: String, behandlingId: Long, pleietrengendeAktørId: String) : BehandlingProsessEventDto {
        return BehandlingProsessEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem.K9SAK,
            saksnummer = saksnummer,
            aktørId = aktørId,
            vedtaksdato = null,
            behandlingId = behandlingId,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            aksjonspunktTilstander = emptyList(),
            pleietrengendeAktørId = pleietrengendeAktørId
        )
    }

}