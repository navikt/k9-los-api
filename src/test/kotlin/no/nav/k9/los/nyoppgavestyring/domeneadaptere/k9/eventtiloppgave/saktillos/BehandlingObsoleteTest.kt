package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos

import io.mockk.every
import io.mockk.mockk
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerKlientLocal
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class BehandlingObsoleteTest : AbstractK9LosIntegrationTest() {
    private lateinit var k9SakTilLosAdapterTjeneste: K9SakTilLosAdapterTjeneste
    private val k9SakBerikerKlientLocal: K9SakBerikerKlientLocal = mockk<K9SakBerikerKlientLocal>()

    @BeforeEach
    fun setUp() {
        k9SakTilLosAdapterTjeneste = K9SakTilLosAdapterTjeneste(
            k9SakEventRepository = get(),
            oppgavetypeTjeneste = get(),
            oppgaveV3Tjeneste = get(),
            config = get(),
            transactionalManager = get(),
            k9SakBerikerKlient = k9SakBerikerKlientLocal,
            pepCacheService = get(),
            oppgaveRepository = get(),
            reservasjonV3Tjeneste = get(),
            historikkvaskChannel = get(named("historikkvaskChannelK9Sak")),
        )
    }

    @Test
    fun `Obsolete ytelse på event gir henlagt resultat`() {
        every { k9SakBerikerKlientLocal.hentBehandling(any()) } returns opprettBehandlingMedFagsakDto(
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            BehandlingResultatType.DELVIS_INNVILGET
        )
        val oppgaveDto = k9SakTilLosAdapterTjeneste.ryddOppObsoleteOgResultatfeilFra2020(
            opprettEvent(FagsakYtelseType.OBSOLETE, BehandlingStatus.UTREDES),
            opprettOppgaveDto(BehandlingResultatType.IKKE_FASTSATT),
            k9SakBerikerKlientLocal.hentBehandling(UUID.randomUUID())
        )

        assertEquals(BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode, oppgaveDto.feltverdier.filter{ it.nøkkel == "resultattype"}.first().verdi)
    }

    @Test
    fun `avsluttet Behandling uten fastsatt resultat skal slå opp i k9-sak`() {
        every { k9SakBerikerKlientLocal.hentBehandling(any()) } returns opprettBehandlingMedFagsakDto(
            FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
            BehandlingResultatType.DELVIS_INNVILGET
        )

        val oppgaveDto = k9SakTilLosAdapterTjeneste.ryddOppObsoleteOgResultatfeilFra2020(
            opprettEvent(FagsakYtelseType.PLEIEPENGER_SYKT_BARN, BehandlingStatus.AVSLUTTET),
            opprettOppgaveDto(BehandlingResultatType.IKKE_FASTSATT),
            k9SakBerikerKlientLocal.hentBehandling(UUID.randomUUID())
        )

        assertEquals(BehandlingResultatType.DELVIS_INNVILGET.kode, oppgaveDto.feltverdier.filter{ it.nøkkel == "resultattype"}.first().verdi)
    }

    @Test
    fun `avsluttet Behandling uten fastsatt resultat skal slå opp i k9-sak, Henlegg oppgaver hvor behandling ikke finnes`() {
        every { k9SakBerikerKlientLocal.hentBehandling(any()) } returns null

        val oppgaveDto = k9SakTilLosAdapterTjeneste.ryddOppObsoleteOgResultatfeilFra2020(
            opprettEvent(FagsakYtelseType.PLEIEPENGER_SYKT_BARN, BehandlingStatus.AVSLUTTET),
            opprettOppgaveDto(BehandlingResultatType.IKKE_FASTSATT),
            k9SakBerikerKlientLocal.hentBehandling(UUID.randomUUID())
        )

        assertEquals(BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode, oppgaveDto.feltverdier.filter{ it.nøkkel == "resultattype"}.first().verdi)
    }

    @Test
    fun `avsluttet Behandling uten fastsatt resultat skal slå opp i k9-sak, Henlegg oppgaver hvor ytelsetype er OBSOLETE`() {
        every { k9SakBerikerKlientLocal.hentBehandling(any()) } returns opprettBehandlingMedFagsakDto(
            FagsakYtelseType.OBSOLETE,
            BehandlingResultatType.INGEN_ENDRING
        )

        val oppgaveDto = k9SakTilLosAdapterTjeneste.ryddOppObsoleteOgResultatfeilFra2020(
            opprettEvent(FagsakYtelseType.PLEIEPENGER_SYKT_BARN, BehandlingStatus.AVSLUTTET),
            opprettOppgaveDto(BehandlingResultatType.IKKE_FASTSATT),
            k9SakBerikerKlientLocal.hentBehandling(UUID.randomUUID())
        )

        assertEquals(BehandlingResultatType.HENLAGT_FEILOPPRETTET.kode, oppgaveDto.feltverdier.filter{ it.nøkkel == "resultattype"}.first().verdi)
    }

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
            aksjonspunktTilstander = emptyList()
        )
    }

    private fun opprettOppgaveDto(resultat: BehandlingResultatType) : OppgaveDto {
        return OppgaveDto(
            id = "12345",
            versjon = "1",
            område = "K9",
            kildeområde = "K9",
            type = "k9-sak",
            status = Oppgavestatus.AAPEN.kode,
            endretTidspunkt = LocalDateTime.now(),
            reservasjonsnøkkel = "12345",
            feltverdier = listOf(OppgaveFeltverdiDto(
                nøkkel = "resultattype",
                verdi = resultat.kode
            )),
        )
    }
}