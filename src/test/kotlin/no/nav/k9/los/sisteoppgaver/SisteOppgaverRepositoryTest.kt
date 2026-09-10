package no.nav.k9.los.sisteoppgaver

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.OppgaveTestDataBuilder
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*

class SisteOppgaverRepositoryTest : AbstractK9LosIntegrationTest() {

    private lateinit var sisteOppgaverRepository: SisteOppgaverRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private lateinit var testSaksbehandlerRepository: TestSaksbehandlerRepository
    private lateinit var transactionalManager: TransactionalManager
    private lateinit var saksbehandler: Saksbehandler

    @BeforeEach
    fun setup() {
        OppgaveTestDataBuilder()
        sisteOppgaverRepository = get()
        saksbehandlerRepository = get()
        testSaksbehandlerRepository = get()
        transactionalManager = get()

        runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(navident = "test", navn = "Test Testersen", epost = "test@nav.no", enhet = null),
                Områder.K9,
                skjermet = false,
            )
            saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost("test@nav.no", skjermet = false)!!
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
                    områdeEksternId = Områder.K9,
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
                    områdeEksternId = Områder.K9,
                    oppgaveEksternId = behandlingUuid2,
                    oppgaveTypeEksternId = "k9sak"
                )
            )
        }

        // Hent siste oppgaver, og sjekk resultatet
        val sisteOppgaver = transactionalManager.transaction { tx ->
            sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost, Områder.K9)
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
                    områdeEksternId = Områder.K9,
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
                    områdeEksternId = Områder.K9,
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
                    områdeEksternId = Områder.K9,
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
                    områdeEksternId = Områder.K9,
                    oppgaveEksternId = behandlingUuid1,
                    oppgaveTypeEksternId = "k9sak"
                )
            )
        }

        // Hent siste oppgaver, og sjekk resultatet
        val sisteOppgaver = transactionalManager.transaction { tx ->
            sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost, Områder.K9)
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
                        områdeEksternId = Områder.K9,
                        oppgaveEksternId = uuid,
                        oppgaveTypeEksternId = "k9sak"
                    )
                )
            }

            transactionalManager.transaction { tx ->
                sisteOppgaverRepository.ryddOppForBrukerIdent(tx, saksbehandler.epost, Områder.K9)
            }
        }

        // Hent siste oppgaver. Sjekk at vi har 10 oppgaver og at den eldste er fjernet
        val sisteOppgaver = transactionalManager.transaction { tx ->
            sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost, Områder.K9)
        }
        assertThat(sisteOppgaver).hasSize(10)
        val eldsteBehandlingUuid = behandlingUuids.first()
        assertThat(sisteOppgaver.none { it.eksternId == eldsteBehandlingUuid }).isTrue()
    }

    @Test
    fun `lagring henting og opprydding isoleres per område også med samme oppgave ider`() {
        transactionalManager.transaction { tx ->
            tx.run(queryOf("INSERT INTO omrade (ekstern_id) VALUES (?) ON CONFLICT DO NOTHING", "AKTIVITETSPENGER").asUpdate)
            tx.run(queryOf(
                """
                    INSERT INTO oppgavetype (ekstern_id, omrade_id, definisjonskilde)
                    SELECT 'aktivitetspenger-test', id, 'test' FROM omrade WHERE ekstern_id = ?
                """.trimIndent(), "AKTIVITETSPENGER"
            ).asUpdate)
        }
        Områder.entries.forEach { område ->
            (1..11).forEach { nummer ->
                transactionalManager.transaction { tx ->
                    sisteOppgaverRepository.lagreSisteOppgave(
                        tx, saksbehandler.epost, OppgaveNøkkelDto(
                            "oppgave-$nummer", if (område == Områder.K9) "k9sak" else "aktivitetspenger-test", område
                        )
                    )
                }
            }
        }

        transactionalManager.transaction { tx ->
            sisteOppgaverRepository.ryddOppForBrukerIdent(tx, saksbehandler.epost, Områder.K9)
            val antall = tx.run(queryOf(
                """
                    SELECT o.ekstern_id, count(*) AS antall FROM siste_oppgaver so
                    JOIN oppgavetype ot ON ot.id = so.oppgavetype_id
                    JOIN omrade o ON o.id = ot.omrade_id
                    WHERE so.bruker_ident = ? GROUP BY o.ekstern_id
                """.trimIndent(), saksbehandler.epost
            ).map { it.string("ekstern_id") to it.int("antall") }.asList).toMap()
            assertThat(antall).isEqualTo(mapOf("K9" to 10, "AKTIVITETSPENGER" to 11))

            Områder.entries.forEach { område ->
                val siste = sisteOppgaverRepository.hentSisteOppgaver(tx, saksbehandler.epost, område)
                assertThat(siste).hasSize(10)
                assertThat(siste.map { it.område }.toSet()).isEqualTo(setOf(område))
                assertThat(siste.none { it.eksternId == "oppgave-1" }).isTrue()
            }
        }
    }
}
