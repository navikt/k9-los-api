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
        val behandlingUuid1 = UUID.randomUUID().toString()
        val behandlingUuid2 = UUID.randomUUID().toString()

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

        // Hent siste oppgaver, og sjekk resultatet
        val sisteOppgaver = transactionalManager.transaction { tx ->
            sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost)
        }
        assertThat(sisteOppgaver).hasSize(2)
        assertThat(sisteOppgaver[0].eksternId).isEqualTo(behandlingUuid2)
        assertThat(sisteOppgaver[1].eksternId).isEqualTo(behandlingUuid1)
    }

    @Test
    fun `skal flytte oppgave til toppen av listen når den lagres på nytt`() {
        val behandlingUuid1 = UUID.randomUUID().toString()
        val behandlingUuid2 = UUID.randomUUID().toString()
        val behandlingUuid3 = UUID.randomUUID().toString()

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

        // Hent siste oppgaver, og sjekk resultatet
        val sisteOppgaver = transactionalManager.transaction { tx ->
            sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost)
        }
        assertThat(sisteOppgaver).hasSize(3)
        assertThat(sisteOppgaver[0].eksternId).isEqualTo(behandlingUuid1) // Oppgave1 skal nå være øverst
        assertThat(sisteOppgaver[1].eksternId).isEqualTo(behandlingUuid3)
        assertThat(sisteOppgaver[2].eksternId).isEqualTo(behandlingUuid2)
    }

    @Test
    fun `skal rydde opp og beholde kun de 10 nyeste oppgavene`() {
        // Opprett 11 oppgaver, og lagre de som siste besøkte
        val behandlingUuids = (1..11).map { UUID.randomUUID().toString() }
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

            transactionalManager.transaction { tx ->
                sisteOppgaverRepository.ryddOppForBrukerIdent(tx, saksbehandler.epost)
            }
        }

        // Hent siste oppgaver. Sjekk at vi har 10 oppgaver og at den eldste er fjernet
        val sisteOppgaver = transactionalManager.transaction { tx ->
            sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost)
        }
        assertThat(sisteOppgaver).hasSize(10)
        val eldsteBehandlingUuid = behandlingUuids.first()
        assertThat(sisteOppgaver.none { it.eksternId == eldsteBehandlingUuid }).isTrue()
    }
}