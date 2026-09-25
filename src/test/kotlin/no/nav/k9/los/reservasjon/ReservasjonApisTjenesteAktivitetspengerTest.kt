package no.nav.k9.los.reservasjon

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventHandler
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktBehandlendeEnhet
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.infrastruktur.rest.CoroutineRequestContext
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.enkeltoppslag.AktivOppgaveOppslag
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.ung.kodeverk.hendelse.EventHendelse
import no.nav.ung.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.ung.sak.typer.Periode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.Områdesetup as AktOmrådesetup
import no.nav.ung.kodeverk.Fagsystem as UngFagsystem

class ReservasjonApisTjenesteAktivitetspengerTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `reserverte aktivitetspenger-oppgaver tolkes av sitt eget område`() {
        get<AktOmrådesetup>().setup()
        val eksternId = UUID.randomUUID()
        opprettAktivitetspengeroppgave(eksternId)

        val oppgave = get<AktivOppgaveOppslag>().hentAktivOppgave(
            Områder.AKTIVITETSPENGER,
            eksternId.toString(),
            "aktivitetspenger-ordinær-del1",
        )

        val saksbehandler = runBlocking {
            get<TestSaksbehandlerRepository>().opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = "Z999999",
                    navn = "Akt Saksbehandler",
                    epost = "akt@nav.no",
                    enhet = null,
                    områder = listOf(Områder.AKTIVITETSPENGER),
                )
            )
        }

        val reservasjoner = runBlocking(CoroutineRequestContext(IdTokenLocal(), Områder.AKTIVITETSPENGER)) {
            get<ReservasjonV3Tjeneste>().taReservasjon(
                Områder.AKTIVITETSPENGER,
                oppgave.reservasjonsnøkkel,
                saksbehandler.id,
                saksbehandler.id,
                null,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(2),
            )
            get<ReservasjonApisTjeneste>().hentReserverteOppgaverForSaksbehandlerNy(Områder.AKTIVITETSPENGER, saksbehandler)
        }

        val reservasjon = reservasjoner.single()
        assertEquals(oppgave.reservasjonsnøkkel, reservasjon.reservasjon.reservasjonsnøkkel)
        assertEquals("akt@nav.no", reservasjon.reservasjon.reservertAvEpost)
        assertNull(reservasjon.reservasjon.kommentar)

        val sammendrag = reservasjon.oppgaver.single()
        assertEquals(eksternId.toString(), sammendrag.oppgaveNøkkel.oppgaveEksternId)
        assertEquals("AKT123456", sammendrag.saksnummer)
        assertEquals(BehandlingType.FØRSTEGANGSSØKNAD.kode, sammendrag.behandlingstype?.kode)
        assertNull(sammendrag.ytelse)
    }

    private fun opprettAktivitetspengeroppgave(eksternId: UUID) {
        val opprettet = LocalDateTime.now().minusDays(3)
        val aksjonspunkt = AksjonspunktDefinisjon.LOKALKONTOR_FORESLÅR_VILKÅR
        val event = UngSakEventDto(
            eksternId = eksternId,
            fagsystem = UngFagsystem.UNG_SAK,
            saksnummer = "AKT123456",
            aktørId = "1234567890123",
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = aksjonspunkt.behandlingSteg.kode,
            behandlendeEnhet = AktBehandlendeEnhet.entries.first { it != AktBehandlendeEnhet.UKJENT }.kode,
            ansvarligBeslutterForTotrinn = null,
            ansvarligSaksbehandlerForTotrinn = null,
            navKontorAnsvarligSaksbehandler = "Z123456",
            navKontorBeslutter = null,
            resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
            ytelseTypeKode = FagsakYtelseType.AKTIVITETSPENGER.kode,
            behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            eldsteDatoMedEndringFraSøker = opprettet,
            opprettetBehandling = opprettet,
            fagsakPeriode = Periode(LocalDate.now().minusMonths(3), LocalDate.now()),
            aksjonspunktTilstander = listOf(
                AksjonspunktTilstandDto(
                    aksjonspunkt.kode,
                    AksjonspunktStatus.OPPRETTET,
                    Venteårsak.UDEFINERT,
                    null,
                    null,
                    opprettet,
                    opprettet,
                )
            ),
            nyeKrav = false,
            vedtaksdato = null,
            behandlingstidFrist = LocalDate.now().plusWeeks(3),
            behandlingsårsaker = emptyList(),
        )
        get<UngSakEventHandler>().prosesser(
            eksternId = eksternId.toString(),
            eksternVersjon = event.eventTid.toString(),
            event = LosObjectMapper.instance.writeValueAsString(event),
        )
        get<EventTilOppgaveAdapter>().oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.UNGSAK, eksternId.toString()))
    }
}
