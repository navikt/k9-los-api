package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.K9SakEventDtoBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.TestSaksbehandler
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker.K9SakBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HistorikkvaskFerdigstiltTest : AbstractK9LosIntegrationTest() {

    private lateinit var transactionalManager: TransactionalManager
    private lateinit var k9SakEventHandler: K9SakEventHandler
    private lateinit var oppgaveRepositoryTxWrapper: OppgaveRepositoryTxWrapper
    private lateinit var oppgaveV3Tjeneste: OppgaveV3Tjeneste
    private lateinit var oppgavetypeRepository: OppgavetypeRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    
    @BeforeEach
    fun setup() {
        OppgaveTestDataBuilder()
        TestSaksbehandler().init()
        transactionalManager = get()
        k9SakEventHandler = get()
        oppgaveRepositoryTxWrapper = get()
        oppgaveV3Tjeneste = get()
        oppgavetypeRepository = get()
        saksbehandlerRepository = get()
    }
    
    @Test
    fun `historikkvask skal sette ferdigstiltEnhet og ferdigstiltTidspunkt på gamle lukkede oppgaver`() {
        val k9SakBerikerKlient = mockk<K9SakBerikerInterfaceKludge>()
        val saksbehandlerId = "Z123456"

        every {
            k9SakBerikerKlient.hentBehandling(any(), any())
        } returns BehandlingMedFagsakDto().apply {
            this.behandlingResultatType = BehandlingResultatType.INNVILGET
            this.sakstype = null
            this.eldsteDatoMedEndringFraSøker = LocalDateTime.now().minusDays(4)
        }
        
        val eksternId = UUID.randomUUID()
        val eventBuilder = K9SakEventDtoBuilder(
            eksternId = eksternId, 
            saksnummer = "SAKSNUMMER123", 
            pleietrengendeAktørId = "PLEIETRENG123"
        )
        
        // Opprett events som vil føre til en ferdigstilt oppgave
        val event1 = eventBuilder.opprettet().apply { ansvarligSaksbehandlerIdent = saksbehandlerId }.build(1)
        val event2 = eventBuilder.avsluttet().build(2)
        k9SakEventHandler.prosesser(event1)
        k9SakEventHandler.prosesser(event2)
        
        // Hent oppgaven og sjekk at den er LUKKET
        val oppgave = oppgaveRepositoryTxWrapper.hentOppgave("K9", eksternId.toString())
        assertEquals(Oppgavestatus.LUKKET.kode, oppgave.status)
        
        // Oppdater oppgaven for å fjerne ferdigstiltEnhet og ferdigstiltTidspunkt feltene
        transactionalManager.transaction { tx ->
            val oppgavetype = oppgavetypeRepository.hentOppgavetype("K9", "k9sak", tx)
            val aktivOppgave = oppgaveV3Tjeneste.hentAktivOppgave(eksternId.toString(), oppgavetype.eksternId, oppgavetype.område.eksternId, tx)
            
            // Fjern feltene for å simulere en gammel oppgave som mangler disse feltene
            val felterUtenFerdigstilt = aktivOppgave.felter.filter { 
                it.oppgavefelt.feltDefinisjon.eksternId != "ferdigstiltEnhet" && 
                it.oppgavefelt.feltDefinisjon.eksternId != "ferdigstiltTidspunkt" 
            }
            
            val oppdatertOppgave = OppgaveV3(
                eksternId = aktivOppgave.eksternId,
                eksternVersjon = aktivOppgave.eksternVersjon,
                oppgavetype = oppgavetype,
                status = aktivOppgave.status,
                kildeområde = aktivOppgave.kildeområde,
                endretTidspunkt = aktivOppgave.endretTidspunkt,
                reservasjonsnøkkel = aktivOppgave.reservasjonsnøkkel,
                felter = felterUtenFerdigstilt,
                aktiv = aktivOppgave.aktiv,
            )
            
            oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppdatertOppgave, 1, tx)
        }
        
        // Verifiser at feltene ikke er satt før historikkvask
        val oppgaveFørVask = oppgaveRepositoryTxWrapper.hentOppgave("K9", eksternId.toString())
        assertNotNull(oppgaveFørVask)
        assertEquals(Oppgavestatus.LUKKET.kode, oppgaveFørVask.status)
        
        val ferdigstiltEnhetFørVask = oppgaveFørVask.felter.find { it.eksternId == "ferdigstiltEnhet" }
        val ferdigstiltTidspunktFørVask = oppgaveFørVask.felter.find { it.eksternId == "ferdigstiltTidspunkt" }
        
        assertTrue(ferdigstiltEnhetFørVask == null, "ferdigstiltEnhet skal ikke være satt før historikkvask")
        assertTrue(ferdigstiltTidspunktFørVask == null, "ferdigstiltTidspunkt skal ikke være satt før historikkvask")
        
        // Utfør historikkvask
        val historikkvaskTjeneste = K9SakTilLosHistorikkvaskTjeneste(
            get(), oppgaveV3Tjeneste, get(), get(), get(), k9SakBerikerKlient
        )
        
        historikkvaskTjeneste.vaskOppgaveForBehandlingUUID(eksternId)
        
        // Verifiser at feltene er satt etter historikkvask
        val oppgaveEtterVask = oppgaveRepositoryTxWrapper.hentOppgave("K9", eksternId.toString())
        assertNotNull(oppgaveEtterVask)
        assertEquals(Oppgavestatus.LUKKET.kode, oppgaveEtterVask.status)
        
        val ferdigstiltEnhetEtterVask = oppgaveEtterVask.felter.find { it.eksternId == "ferdigstiltEnhet" }
        val ferdigstiltTidspunktEtterVask = oppgaveEtterVask.felter.find { it.eksternId == "ferdigstiltTidspunkt" }

        // Verifiser at verdiene er riktige
        assertEquals(
            "NAV DRIFT",
            ferdigstiltEnhetEtterVask?.verdi,
            "ferdigstiltEnhet skal være satt til saksbehandlerens enhet"
        )
        assertThat(ferdigstiltTidspunktEtterVask?.verdi?.isNotBlank()).isEqualTo(true)
    }
}