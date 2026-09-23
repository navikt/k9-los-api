package no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.saktillos

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.EventHendelse
import no.nav.k9.los.domeneadaptere.eventmottak.k9.sak.K9SakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventBeriker
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.klagetillos.beriker.K9KlageBerikerInterfaceKludge
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.kodeverk.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.SakEventTilOppgaveMapper
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.beriker.K9SakSystemKlientLocal
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.kodeverk.BehandlingStatus
import no.nav.k9.los.kodeverk.BehandlingType
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavemottak.OppgaveDto
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class BehandlingObsoleteTest : AbstractK9LosIntegrationTest() {
    private lateinit var eventBeriker: EventBeriker
    private val k9SakBerikerKlientLocal = mockk<K9SakSystemKlientLocal>()

    @BeforeEach
    fun setUp() {
        eventBeriker = EventBeriker(
            k9SakBeriker = k9SakBerikerKlientLocal,
            k9KlageBeriker = mockk<K9KlageBerikerInterfaceKludge>(),
        )
    }

    @Test
    fun `Obsolete ytelse på event gir henlagt resultat`() {
        val oppgaveDto = SakEventTilOppgaveMapper.ryddOppObsoleteYtelse(
            opprettEvent(FagsakYtelseType.OBSOLETE, BehandlingStatus.UTREDES),
            opprettOppgaveDto(BehandlingResultatType.IKKE_FASTSATT),
        )

        assertEquals(Oppgavestatus.LUKKET, oppgaveDto.status)
        assertEquals(
            BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode,
            oppgaveDto.feltverdier.first { it.nøkkel == "resultattype" }.verdi
        )
    }

    @Test
    fun `Obsolete ytelse på event skal ikke slå opp i k9-sak`() {
        val beriket = eventBeriker.berik(
            lagret(opprettEvent(FagsakYtelseType.OBSOLETE, BehandlingStatus.AVSLUTTET))
        ) as EventLagret.K9Sak

        assertEquals(null, beriket.eventDto.resultatType)
        verify(exactly = 0) { k9SakBerikerKlientLocal.hentBehandling(any()) }
    }

    @Test
    fun `avsluttet Behandling uten fastsatt resultat skal slå opp i k9-sak`() {
        every { k9SakBerikerKlientLocal.hentBehandling(any()) } returns opprettBehandlingMedFagsakDto(
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            BehandlingResultatType.DELVIS_INNVILGET
        )

        val beriket = berikAvsluttetUtenResultat()

        assertEquals(BehandlingResultatType.DELVIS_INNVILGET.kode, beriket.eventDto.resultatType)
    }

    @Test
    fun `avsluttet Behandling uten fastsatt resultat skal slå opp i k9-sak, Henlegg oppgaver hvor behandling ikke finnes`() {
        every { k9SakBerikerKlientLocal.hentBehandling(any()) } returns null

        val beriket = berikAvsluttetUtenResultat()

        assertEquals(BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode, beriket.eventDto.resultatType)
    }

    @Test
    fun `avsluttet Behandling uten fastsatt resultat skal slå opp i k9-sak, Henlegg oppgaver hvor ytelsetype er OBSOLETE`() {
        every { k9SakBerikerKlientLocal.hentBehandling(any()) } returns opprettBehandlingMedFagsakDto(
            FagsakYtelseType.OBSOLETE,
            BehandlingResultatType.INGEN_ENDRING
        )

        val beriket = berikAvsluttetUtenResultat()

        assertEquals(BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode, beriket.eventDto.resultatType)
    }

    @Test
    fun `eventserie for samme behandling skal bare gi ett oppslag mot k9-sak`() {
        every { k9SakBerikerKlientLocal.hentBehandling(any()) } returns opprettBehandlingMedFagsakDto(
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            BehandlingResultatType.DELVIS_INNVILGET
        )
        val event = opprettEvent(FagsakYtelseType.PLEIEPENGER_SYKT_BARN, BehandlingStatus.AVSLUTTET)

        val berikede = eventBeriker.berik(listOf(lagret(event), lagret(event), lagret(event)))

        berikede.forEach {
            assertEquals(BehandlingResultatType.DELVIS_INNVILGET.kode, (it as EventLagret.K9Sak).eventDto.resultatType)
        }
        verify(exactly = 1) { k9SakBerikerKlientLocal.hentBehandling(any()) }
    }

    private fun berikAvsluttetUtenResultat(): EventLagret.K9Sak =
        eventBeriker.berik(
            lagret(opprettEvent(FagsakYtelseType.PLEIEPENGER_SYKT_BARN, BehandlingStatus.AVSLUTTET))
        ) as EventLagret.K9Sak

    private fun lagret(event: K9SakEventDto) = EventLagret.K9Sak(
        nøkkelId = 1L,
        eksternId = event.eksternId.toString(),
        eksternVersjon = event.eventTid.toString(),
        eventJson = LosObjectMapper.instance.writeValueAsString(event),
        opprettet = LocalDateTime.now(),
        dirty = true,
    )

    private fun opprettBehandlingMedFagsakDto(fagsakYtelseType: FagsakYtelseType, behandlingResultatType: BehandlingResultatType) : BehandlingMedFagsakDto {
        val dto = BehandlingMedFagsakDto()
        dto.sakstype = fagsakYtelseType
        dto.behandlingResultatType = behandlingResultatType
        return dto
    }

    private fun opprettEvent(fagsakYtelseType: FagsakYtelseType, behandlingStatus: BehandlingStatus) : K9SakEventDto {
        return K9SakEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem.K9SAK,
            saksnummer = "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            behandlingStatus = behandlingStatus.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = fagsakYtelseType.kode,
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = emptyMap<String, String>().toMutableMap(),
            aksjonspunktTilstander = emptyList(),
            merknader = emptyList()
        )
    }

    private fun opprettOppgaveDto(resultat: BehandlingResultatType) : OppgaveDto {
        return OppgaveDto(
            eksternId = "12345",
            eksternVersjon = "1",
            type = K9Oppgavetypenavn.SAK,
            status = Oppgavestatus.AAPEN,
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = "12345",
            feltverdier = listOf(OppgaveFeltverdiDto(
                nøkkel = "resultattype",
                verdi = resultat.kode
            )),
        )
    }
}

