package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import assertk.assertThat
import assertk.assertions.contains
import kotlinx.coroutines.runBlocking
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.aksjonspunktbehandling.K9sakEventHandler
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class ReservasjonOversetterTest : AbstractK9LosIntegrationTest() {
    lateinit var områdeSetup: OmrådeSetup
    lateinit var saksbehandlerRepository: SaksbehandlerRepository
    lateinit var k9sakEventHandler: K9sakEventHandler
    lateinit var reservasjonOversetter: ReservasjonOversetter
    lateinit var oppgaveV3Repository: OppgaveRepositoryTxWrapper
    lateinit var transactionalManager: TransactionalManager

    var saraSaksbehandlerId: Long = 0L
    var birgerSaksbehandlerId: Long = 0L

    @BeforeEach
    fun setup() {
        områdeSetup = get<OmrådeSetup>()
        områdeSetup.setup()
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        k9SakTilLosAdapterTjeneste.setup()

        saksbehandlerRepository = get<SaksbehandlerRepository>()

        runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "Z123456",
                    navn = "Saksbehandler Sara",
                    epost = "saksbehandler@nav.no",
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


        }
        saraSaksbehandlerId = saksbehandlerRepository.finnSaksbehandlerIdForIdent("Z123456")!!
        birgerSaksbehandlerId = saksbehandlerRepository.finnSaksbehandlerIdForIdent("Z123457")!!

        k9sakEventHandler = get<K9sakEventHandler>()
        reservasjonOversetter = get<ReservasjonOversetter>()

        oppgaveV3Repository = get<OppgaveRepositoryTxWrapper>()
        transactionalManager = get<TransactionalManager>()
    }

    @Test
    fun `alle reservasjoner skal nå gå normalt mot V3 - Ingen nye legacy-reservasjoner`() {
        val behandling1 = mockEvent("1234", aktørId = "1234567890", behandlingId = 123L, "11111111111")
        val behandling2 = mockEvent("1235", aktørId = "1234567891", behandlingId = 123L, "11111111111")

        k9sakEventHandler.prosesser(behandling1)
        k9sakEventHandler.prosesser(behandling2)

        val oppgaveRepository = get<OppgaveRepository>()
        val oppgave1 = oppgaveRepository.hent(behandling1.eksternId!!)

        val reservasjonOversetter = get<ReservasjonOversetter>()

        // Alle reservasjoner skal nå være på nytt format
        val reservasjonV3 = reservasjonOversetter.taNyReservasjonFraGammelKontekst(
            oppgaveV1 = oppgave1,
            reserverForSaksbehandlerId = saraSaksbehandlerId,
            reservertTil = LocalDateTime.now().plusDays(1),
            utførtAvSaksbehandlerId = saraSaksbehandlerId,
            kommentar = "test"
        )

        assertEquals(saraSaksbehandlerId, reservasjonV3.reservertAv)
        assertFalse(reservasjonV3.reservasjonsnøkkel.contains("legacy"))

        val nyOppgaveRepository = get<OppgaveRepositoryTxWrapper>()
        val reserverteOppgaver =
            nyOppgaveRepository.hentÅpneOppgaverForReservasjonsnøkkel(reservasjonV3.reservasjonsnøkkel)
        assertEquals(2, reserverteOppgaver.size)
    }

    @Test
    fun `reservasjonOversetter -- legacyreservasjoner skal skjule og ekskludere rene v3-reservasjoner - konvertering erstatter med ren reservasjon`() {
        val nå = LocalDateTime.now()
        val behandling1 = mockEvent("1234", aktørId = "1234567890", behandlingId = 123L, "11111111111")
        val behandling2 = mockEvent("1235", aktørId = "1234567891", behandlingId = 123L, "11111111111")

        k9sakEventHandler.prosesser(behandling1)
        k9sakEventHandler.prosesser(behandling2)

        val oppgaveRepository = get<OppgaveRepository>()
        val oppgave = oppgaveRepository.hent(behandling1.eksternId!!)

        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()
        transactionalManager.transaction { tx ->
            reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                reservasjonsnøkkel = "legacy_${oppgave.eksternId}",
                reserverForId = saraSaksbehandlerId,
                gyldigFra = nå.minusDays(1),
                gyldigTil = nå.plusDays(2),
                utføresAvId = saraSaksbehandlerId,
                kommentar = "test",
                tx = tx
            )
        }

        val legacyReservasjon = reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(oppgave)
        assertNotNull(legacyReservasjon)

        val reservasjonV3 = reservasjonOversetter.taNyReservasjonFraGammelKontekst(
            oppgaveV1 = oppgave,
            reserverForSaksbehandlerId = saraSaksbehandlerId,
            reservertTil = nå.plusDays(3),
            utførtAvSaksbehandlerId = saraSaksbehandlerId,
            kommentar = "test",
        )
        assertNotNull(reservasjonV3)
        assertThat(reservasjonV3.reservasjonsnøkkel).contains("legacy") //skal få den gamle reservasjonen tilbake

        val reservasjonKonverteringJobb = get<ReservasjonKonverteringJobb>()
        reservasjonKonverteringJobb.spillAvReservasjoner()

        val konvertertReservasjon = reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(oppgave)
        assertNotNull(konvertertReservasjon)
        assertFalse(konvertertReservasjon!!.reservasjonsnøkkel.contains("legacy"))
        assertEquals(saraSaksbehandlerId, konvertertReservasjon.reservertAv)

        val slettetReservasjon =
            reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel("legacy_${reservasjonV3.reservasjonsnøkkel}")
        assertNull(slettetReservasjon)
    }

    @Test
    fun `reservasjonV3Tjeneste -- legacyreservasjoner skal skjule og ekskludere rene v3-reservasjoner - konvertering erstatter med ren reservasjon`() {
        val nå = LocalDateTime.now()
        val behandling1 = mockEvent("1234", aktørId = "1234567890", behandlingId = 123L, "11111111111")
        val behandling2 = mockEvent("1235", aktørId = "1234567891", behandlingId = 123L, "11111111111")

        k9sakEventHandler.prosesser(behandling1)
        k9sakEventHandler.prosesser(behandling2)

        val oppgaveRepository = get<OppgaveRepository>()
        val oppgave = oppgaveRepository.hent(behandling1.eksternId!!)

        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()
        transactionalManager.transaction { tx ->
            reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                reservasjonsnøkkel = "legacy_${oppgave.eksternId}",
                reserverForId = saraSaksbehandlerId,
                gyldigFra = nå.minusDays(1),
                gyldigTil = nå.plusDays(2),
                utføresAvId = saraSaksbehandlerId,
                kommentar = "test",
                tx = tx,
            )
        }

        val oppgaveV3 = oppgaveV3Repository.hentOppgave("K9", behandling1.eksternId.toString())

        val aktivReservasjon =
            reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(oppgaveV3.reservasjonsnøkkel)!!
        assertTrue(aktivReservasjon.reservasjonsnøkkel.contains("legacy_"))

        var reservasjon = transactionalManager.transaction { tx ->
            reservasjonV3Tjeneste.taReservasjonMenSjekkLegacyFørst(
                reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
                reserverForId = saraSaksbehandlerId,
                utføresAvId = saraSaksbehandlerId,
                kommentar = "test",
                gyldigFra = nå.minusDays(1),
                gyldigTil = nå.plusDays(2),
                tx = tx,
            )
        }
        assertTrue(reservasjon!!.reservasjonsnøkkel.contains("legacy_"))


        reservasjon = reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktivMenSjekkLegacyFørst(
        reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
        reserverForId = saraSaksbehandlerId,
        utføresAvId = saraSaksbehandlerId,
        kommentar = "test",
        gyldigFra = nå.minusDays(1),
        gyldigTil = nå.plusDays(2)
        )
        assertTrue(reservasjon.reservasjonsnøkkel.contains("legacy_"))

        reservasjon = reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(oppgaveV3.reservasjonsnøkkel)!!
        assertTrue(reservasjon.reservasjonsnøkkel.contains("legacy_"))

        val forlengetReservasjon = reservasjonV3Tjeneste.forlengReservasjon(
            oppgaveV3.reservasjonsnøkkel,
            nå.plusDays(5),
            saraSaksbehandlerId,
            "test"
        )
        assertTrue(forlengetReservasjon.reservasjonV3.reservasjonsnøkkel.contains("legacy_"))

        val overførtReservasjon = reservasjonV3Tjeneste.overførReservasjon(
            oppgaveV3.reservasjonsnøkkel,
            nå.plusDays(5),
            birgerSaksbehandlerId,
            saraSaksbehandlerId,
            "test"
        )
        assertTrue(overførtReservasjon.reservasjonV3.reservasjonsnøkkel.contains("legacy_"))

        val endretReservasjon = reservasjonV3Tjeneste.endreReservasjon(
            oppgaveV3.reservasjonsnøkkel,
            saraSaksbehandlerId,
            nå.plusDays(5),
            birgerSaksbehandlerId,
            "test"
        )
        assertTrue(endretReservasjon.reservasjonV3.reservasjonsnøkkel.contains("legacy_"))

        val reservasjonKonverteringJobb = get<ReservasjonKonverteringJobb>()
        reservasjonKonverteringJobb.spillAvReservasjoner()

        val konvertertReservasjon = reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(oppgave)
        assertNotNull(konvertertReservasjon)
        assertFalse(konvertertReservasjon!!.reservasjonsnøkkel.contains("legacy"))
        assertEquals(birgerSaksbehandlerId, konvertertReservasjon.reservertAv)
    }

fun mockEvent(
    saksnummer: String,
    aktørId: String,
    behandlingId: Long,
    pleietrengendeAktørId: String
): BehandlingProsessEventDto {
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