package no.nav.k9.los.nyoppgavestyring.sisteoppgaver

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*

class SisteOppgaverRepositoryTest : AbstractK9LosIntegrationTest() {

    private lateinit var sisteOppgaverRepository: SisteOppgaverRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private lateinit var transactionalManager: TransactionalManager
    private lateinit var saksbehandler: Saksbehandler

    @BeforeEach
    fun setup() {
        OppgaveTestDataBuilder()
        sisteOppgaverRepository = get()
        saksbehandlerRepository = get()
        transactionalManager = get()

        // Opprette en testbruker
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
    fun `skal lagre og hente siste oppgaver for en saksbehandler`() {
        // Opprett to oppgaver
        val behandlingUuid1 = UUID.randomUUID().toString()
        val behandlingUuid2 = UUID.randomUUID().toString()

        // Lagre oppgavene som siste besøkte
        transactionalManager.transaction { tx ->
            sisteOppgaverRepository.lagreSisteOppgave(
                tx,
                saksbehandler.epost,
                OppgaveNøkkelDto(
                    områdeEksternId = "K9",
                    oppgaveEksternId = behandlingUuid1,
                    oppgaveTypeEksternId = "k9sak"
                )
            )
        }

        transactionalManager.transaction { tx ->
            sisteOppgaverRepository.lagreSisteOppgave(
                tx,
                saksbehandler.epost,
                OppgaveNøkkelDto(
                    områdeEksternId = "K9",
                    oppgaveEksternId = behandlingUuid2,
                    oppgaveTypeEksternId = "k9sak"
                )
            )
        }

        // Hent siste oppgaver
        val sisteOppgaver = transactionalManager.transaction { tx ->
            sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost)
        }

        // Sjekk resultatet
        assertThat(sisteOppgaver).hasSize(2)
        assertThat(sisteOppgaver[0].eksternId).isEqualTo(behandlingUuid2)
        assertThat(sisteOppgaver[1].eksternId).isEqualTo(behandlingUuid1)
    }

    @Test
    fun `skal flytte oppgave til toppen av listen når den lagres på nytt`() {
        // Opprett tre oppgaver
        val behandlingUuid1 = UUID.randomUUID().toString()
        val behandlingUuid2 = UUID.randomUUID().toString()
        val behandlingUuid3 = UUID.randomUUID().toString()

        // Lagre oppgavene som siste besøkte i rekkefølge
        transactionalManager.transaction { tx ->
            sisteOppgaverRepository.lagreSisteOppgave(
                tx,
                saksbehandler.epost,
                OppgaveNøkkelDto(
                    områdeEksternId = "K9",
                    oppgaveEksternId = behandlingUuid1,
                    oppgaveTypeEksternId = "k9sak"
                )
            )
        }

        transactionalManager.transaction { tx ->
            sisteOppgaverRepository.lagreSisteOppgave(
                tx,
                saksbehandler.epost,
                OppgaveNøkkelDto(
                    områdeEksternId = "K9",
                    oppgaveEksternId = behandlingUuid2,
                    oppgaveTypeEksternId = "k9sak"
                )
            )
        }

        transactionalManager.transaction { tx ->
            sisteOppgaverRepository.lagreSisteOppgave(
                tx,
                saksbehandler.epost,
                OppgaveNøkkelDto(
                    områdeEksternId = "K9",
                    oppgaveEksternId = behandlingUuid3,
                    oppgaveTypeEksternId = "k9sak"
                )
            )
        }


        // Lagre den første oppgaven på nytt - den skal da flyttes til toppen
        transactionalManager.transaction { tx ->
            sisteOppgaverRepository.lagreSisteOppgave(
                tx,
                saksbehandler.epost,
                OppgaveNøkkelDto(
                    områdeEksternId = "K9",
                    oppgaveEksternId = behandlingUuid1,
                    oppgaveTypeEksternId = "k9sak"
                )
            )
        }

        // Hent siste oppgaver
        val sisteOppgaver = transactionalManager.transaction { tx ->
            sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost)
        }

        // Sjekk at oppgave1 nå er øverst
        assertThat(sisteOppgaver).hasSize(3)
        assertThat(sisteOppgaver[0].eksternId).isEqualTo(behandlingUuid1)
        assertThat(sisteOppgaver[1].eksternId).isEqualTo(behandlingUuid3)
        assertThat(sisteOppgaver[2].eksternId).isEqualTo(behandlingUuid2)
    }

    @Test
    fun `skal rydde opp og beholde kun de 10 nyeste oppgavene`() {
        // Opprett 11 oppgaver
        val behandlingUuids = (1..11).map { UUID.randomUUID().toString() }

        // Lagre alle 11 oppgaver som siste besøkte
        behandlingUuids.forEach { uuid ->
            transactionalManager.transaction { tx ->
                sisteOppgaverRepository.lagreSisteOppgave(
                    tx,
                    saksbehandler.epost,
                    OppgaveNøkkelDto(
                        områdeEksternId = "K9",
                        oppgaveEksternId = uuid,
                        oppgaveTypeEksternId = "k9sak"
                    )
                )
            }

            // Kall ryddOpp-metoden
            transactionalManager.transaction { tx ->
                sisteOppgaverRepository.ryddOppForBrukerIdent(tx, saksbehandler.epost)
            }
        }

        // Hent siste oppgaver
        val sisteOppgaver = transactionalManager.transaction { tx ->
            sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost)
        }

        // Sjekk at vi har 10 oppgaver og at den eldste er fjernet
        assertThat(sisteOppgaver).hasSize(10)
        val eldsteBehandlingUuid = behandlingUuids.first()
        assertThat(sisteOppgaver.none { it.eksternId == eldsteBehandlingUuid }).isTrue()
    }
}