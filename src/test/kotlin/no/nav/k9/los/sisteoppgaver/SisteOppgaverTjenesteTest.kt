package no.nav.k9.los.sisteoppgaver

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.FeltType
import no.nav.k9.los.OppgaveTestDataBuilder
import no.nav.k9.los.infrastruktur.abac.Action
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.pdl.IPdlService
import no.nav.k9.los.infrastruktur.pdl.PersonPdl
import no.nav.k9.los.infrastruktur.pdl.PersonPdlResponse
import no.nav.k9.los.infrastruktur.rest.CoroutineRequestContext
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.oppgaveuthenting.OppgaveRepository
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*
import kotlinx.coroutines.withContext

class SisteOppgaverTjenesteTest : AbstractK9LosIntegrationTest() {

    private lateinit var sisteOppgaverRepository: SisteOppgaverRepository
    private lateinit var oppgaveRepository: OppgaveRepository
    private lateinit var transactionalManager: TransactionalManager
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private lateinit var testSaksbehandlerRepository: TestSaksbehandlerRepository
    private lateinit var saksbehandler: Saksbehandler
    
    // Mocks
    private lateinit var pepClient: IPepClient
    private lateinit var pdlService: IPdlService
    private lateinit var sisteOppgaverTjeneste: SisteOppgaverTjeneste

    @BeforeEach
    fun setup() {
        sisteOppgaverRepository = get()
        oppgaveRepository = get()
        transactionalManager = get()
        saksbehandlerRepository = get()
        testSaksbehandlerRepository = get()
        pepClient = mockk(relaxed = true)
        pdlService = mockk(relaxed = true)
        sisteOppgaverTjeneste = SisteOppgaverTjeneste(
            sisteOppgaverRepository = sisteOppgaverRepository,
            oppgaveRepository = oppgaveRepository,
            pepClient = pepClient,
            pdlService = pdlService,
            transactionalManager = transactionalManager
        )
        
        runBlocking {
            saksbehandler = testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = "test",
                    navn = "Test Testersen",
                    epost = "test@nav.no",
                    enhet = null,
                )
            )
        }
    }

    @Test
    fun `skal lagre og hente siste oppgaver`() = runTest {
        withSaksbehandlerRequestContext {
            val aktorId1 = "1234567890123"

            val oppgave1 = OppgaveTestDataBuilder()
                .medOppgaveFeltVerdi(FeltType.AKTØR_ID, aktorId1)
                .lagOgLagre()

            val mockPerson: PersonPdl = mockk(relaxed = true)
            coEvery { pdlService.person(aktorId1) } returns PersonPdlResponse(false, mockPerson)

            coEvery {
                pepClient.harTilgangTilOppgaveV3(any(), eq(Action.read))
            } returns true

            sisteOppgaverTjeneste.lagreSisteOppgave(
                OppgaveNøkkelDto(
                    områdeEksternId = "K9",
                    oppgaveEksternId = oppgave1.eksternId,
                    oppgaveTypeEksternId = oppgave1.oppgavetype.eksternId
                )
            )

            val sisteOppgaver = sisteOppgaverTjeneste.hentSisteOppgaver()
            assertThat(sisteOppgaver).hasSize(1)
            assertThat(sisteOppgaver[0].oppgaveEksternId).isEqualTo(oppgave1.eksternId)
        }
    }
    
    @Test
    fun `skal filtrere bort oppgaver som bruker ikke har tilgang til`() = runTest {
        withSaksbehandlerRequestContext {
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

            coEvery {
                pepClient.harTilgangTilOppgaveV3(any(), eq(Action.read))
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

            val sisteOppgaver = sisteOppgaverTjeneste.hentSisteOppgaver()
            assertThat(sisteOppgaver).hasSize(1)
            assertThat(sisteOppgaver[0].oppgaveEksternId).isEqualTo(oppgave1.eksternId)
        }
    }

    private suspend fun <T> withSaksbehandlerRequestContext(block: suspend () -> T): T {
        val idToken = mockk<IIdToken>()
        every { idToken.getNavIdent() } returns "test"
        return withContext(CoroutineRequestContext(idToken)) { block() }
    }
}
