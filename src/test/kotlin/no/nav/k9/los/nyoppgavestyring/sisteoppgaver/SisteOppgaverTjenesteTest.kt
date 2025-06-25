package no.nav.k9.los.nyoppgavestyring.sisteoppgaver

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Action
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.Auditlogging
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.PersonPdlResponse
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get

class SisteOppgaverTjenesteTest : AbstractK9LosIntegrationTest() {

    private lateinit var sisteOppgaverRepository: SisteOppgaverRepository
    private lateinit var oppgaveRepository: OppgaveRepository
    private lateinit var transactionalManager: TransactionalManager
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private lateinit var saksbehandler: Saksbehandler
    
    // Mocks
    private lateinit var pepClient: IPepClient
    private lateinit var pdlService: IPdlService
    private lateinit var azureGraphService: IAzureGraphService
    private lateinit var sisteOppgaverTjeneste: SisteOppgaverTjeneste

    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @BeforeEach
    fun setup() {
        sisteOppgaverRepository = get()
        oppgaveRepository = get()
        transactionalManager = get()
        saksbehandlerRepository = get()
        pepClient = mockk(relaxed = true)
        pdlService = mockk(relaxed = true)
        azureGraphService = mockk(relaxed = true)
        
        coEvery { azureGraphService.hentIdentTilInnloggetBruker() } returns "test@nav.no"

        sisteOppgaverTjeneste = SisteOppgaverTjeneste(
            sisteOppgaverRepository = sisteOppgaverRepository,
            oppgaveRepository = oppgaveRepository,
            pepClient = pepClient,
            pdlService = pdlService,
            azureGraphService = azureGraphService,
            transactionalManager = transactionalManager
        )
        
        runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "test",
                    navn = "Test Testersen",
                    epost = "test@nav.no",
                    reservasjoner = mutableSetOf(),
                    enhet = null,
                )
            )
            saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost("test@nav.no")!!
        }
    }

    @Test
    fun `skal lagre og hente siste oppgaver`() = runTest {
        val aktorId1 = "1234567890123"
        
        val oppgave1 = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.AKTØR_ID, aktorId1)
            .lagOgLagre()
            
        val mockPerson: PersonPdl = mockk(relaxed = true)
        coEvery { pdlService.person(aktorId1) } returns PersonPdlResponse(false, mockPerson)

        coEvery {
            pepClient.harTilgangTilOppgaveV3(any(), eq(Action.read), eq(Auditlogging.IKKE_LOGG))
        } returns true

        // Lagre oppgaven som siste besøkt
        sisteOppgaverTjeneste.lagreSisteOppgave(
            OppgaveNøkkelDto(
                områdeEksternId = "K9",
                oppgaveEksternId = oppgave1.eksternId,
                oppgaveTypeEksternId = oppgave1.oppgavetype.eksternId
            )
        )
        
        // Hent siste oppgaver, og sjekk resultatet
        val sisteOppgaver = sisteOppgaverTjeneste.hentSisteOppgaver()
        assertThat(sisteOppgaver).hasSize(1)
        assertThat(sisteOppgaver[0].oppgaveEksternId).isEqualTo(oppgave1.eksternId)
    }
    
    @Test
    fun `skal filtrere bort oppgaver som bruker ikke har tilgang til`() = runTest {
        val aktorId1 = "1234567890123"
        val aktorId2 = "9876543210987"

        val oppgave1 = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.AKTØR_ID, aktorId1)
            .lagOgLagre()

        val oppgave2 = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.AKTØR_ID, aktorId2)
            .lagOgLagre()

        val mockPerson: PersonPdl = mockk(relaxed = true)
        coEvery { pdlService.person(aktorId1) } returns PersonPdlResponse(false, mockPerson)
        coEvery { pdlService.person(aktorId2) } returns PersonPdlResponse(true, mockPerson)
        
        // Bruker har tilgang til oppgave1 men ikke oppgave2
        coEvery {
            pepClient.harTilgangTilOppgaveV3(any(), eq(Action.read), eq(Auditlogging.IKKE_LOGG))
        } answers {
            val oppgave = firstArg<Oppgave>()
            oppgave.eksternId == oppgave1.eksternId
        }
        
        sisteOppgaverTjeneste.lagreSisteOppgave(
            OppgaveNøkkelDto(
                områdeEksternId = "K9",
                oppgaveEksternId = oppgave1.eksternId,
                oppgaveTypeEksternId = "k9sak"
            )
        )
        
        sisteOppgaverTjeneste.lagreSisteOppgave(
            OppgaveNøkkelDto(
                områdeEksternId = "K9",
                oppgaveEksternId = oppgave2.eksternId,
                oppgaveTypeEksternId = "k9sak"
            )
        )

        // Sjekk resultatet - skal kun få oppgave1 tilbake siden bruker ikke har tilgang til oppgave2
        val sisteOppgaver = runBlocking { sisteOppgaverTjeneste.hentSisteOppgaver() }
        assertThat(sisteOppgaver).hasSize(1)
        assertThat(sisteOppgaver[0].oppgaveEksternId).isEqualTo(oppgave1.eksternId)
    }
}