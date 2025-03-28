package no.nav.k9.los.nyoppgavestyring

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3PartisjonertRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime

class OppgaveV3PartisjonertRepositoryTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `Sjekker at oppgaver blir lagret i partisjonerte tabeller`() {
        // Setup
        val oppgaveBuilder = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, "test-1234")
            .medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "test-type")
            .medOppgaveFeltVerdi(FeltType.FAGSYSTEM, "test-fagsystem")
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "test-aksjonspunkt")
            .medOppgaveFeltVerdi(FeltType.RESULTAT_TYPE, "test-resultattype")
            .medOppgaveFeltVerdi(FeltType.TOTRINNSKONTROLL, "true")
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGSSTATUS, "test-behandlingsstatus")
            .medOppgaveFeltVerdi(FeltType.YTELSE_TYPE, "test-ytelsetype")
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, LocalDateTime.now().toString())
            .medOppgaveFeltVerdi(FeltType.REGISTRERT_DATO, LocalDateTime.now().toString())
            .medOppgaveFeltVerdi(FeltType.AVVENTER_ARBEIDSGIVER, "false")
            .medOppgaveFeltVerdi(FeltType.LØSBART_AKSJONSPUNKT, "test-løsbart-aksjonspunkt")
            .medOppgaveFeltVerdi(FeltType.LIGGER_HOS_BESLUTTER, "false")

        val oppgave = oppgaveBuilder.lag(status = Oppgavestatus.AAPEN)
        val oppgavePartisjonertRepo = get<OppgaveV3PartisjonertRepository>()
        val transactionalManager = get<TransactionalManager>()

        // Test
        transactionalManager.transaction { tx ->
            oppgavePartisjonertRepo.ajourhold(oppgave, tx)
        }

        // Verifiser data i databasen
        val oppgaverIDb = hentOppgaverFraPartisjonertTabell(oppgave.eksternId)
        val feltVerdierIDb = hentFeltVerdierFraPartisjonertTabell(oppgave.eksternId)

        // Assertions
        assertNotNull(oppgaverIDb, "Oppgave skal være lagret i partisjonert tabell")
        assertEquals(1, oppgaverIDb.size, "Det skal være én oppgave i tabellen")
        assertEquals(oppgave.eksternId, oppgaverIDb[0].first, "Ekstern ID skal være lik")
        assertEquals(oppgave.status.kode, oppgaverIDb[0].second, "Status skal være lik")

        assertNotNull(feltVerdierIDb, "Feltverdier skal være lagret i partisjonert tabell")
        assertEquals(oppgave.felter.size, feltVerdierIDb.size, "Antall feltverdier skal være lik")
        
        // Verifiser at alle feltverdier er lagret
        val felterMap = oppgave.felter.associateBy { it.oppgavefelt.feltDefinisjon.eksternId }
        feltVerdierIDb.forEach { (feltId, verdi) ->
            assertEquals(felterMap[feltId]?.verdi, verdi, "Verdien for felt $feltId skal være lik")
        }
    }

    @Test
    fun `Sjekker at oppgaver blir oppdatert i partisjonerte tabeller`() {
        // Setup
        val oppgaveBuilder = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, "test-update-1234")
            .medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "test-type")
            .medOppgaveFeltVerdi(FeltType.FAGSYSTEM, "test-fagsystem")
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "test-aksjonspunkt")

        val oppgave = oppgaveBuilder.lag(status = Oppgavestatus.AAPEN)
        val oppgavePartisjonertRepo = get<OppgaveV3PartisjonertRepository>()
        val transactionalManager = get<TransactionalManager>()

        // Lagre oppgave første gang
        transactionalManager.transaction { tx ->
            oppgavePartisjonertRepo.ajourhold(oppgave, tx)
        }

        // Oppdater oppgave
        val oppdatertOppgave = oppgaveBuilder
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "endret-aksjonspunkt")
            .lag(status = Oppgavestatus.LUKKET, eksternVersjon = (oppgave.eksternVersjon.toInt() + 1).toString())

        // Lagre oppgave på nytt med endrede verdier
        transactionalManager.transaction { tx ->
            oppgavePartisjonertRepo.ajourhold(oppdatertOppgave, tx)
        }

        // Verifiser data i databasen
        val feltVerdierIDb = hentFeltVerdierFraPartisjonertTabell(oppdatertOppgave.eksternId)

        // Assertions
        val aksjonspunktVerdi = feltVerdierIDb.find { it.first == FeltType.AKSJONSPUNKT.eksternId }?.second
        assertEquals("endret-aksjonspunkt", aksjonspunktVerdi, "Aksjonspunkt verdi skal være oppdatert")
        
        val oppgaverIDb = hentOppgaverFraPartisjonertTabell(oppdatertOppgave.eksternId)
        assertEquals(Oppgavestatus.LUKKET.kode, oppgaverIDb[0].second, "Status skal være oppdatert til LUKKET")
    }

    private fun hentOppgaverFraPartisjonertTabell(eksternId: String): List<Pair<String, String>> {
        return get<TransactionalManager>().transaction { tx ->
            tx.run(
                kotliquery.queryOf(
                    """
                    SELECT oid.oppgave_ekstern_id, oppgavestatus FROM oppgave_v3_part o INNER JOIN oppgave_id_part oid ON o.id = oid.id
                    WHERE oid.oppgave_ekstern_id = :eksternId
                    """.trimIndent(),
                    mapOf("eksternId" to eksternId)
                ).map { row -> Pair(row.string("oppgave_ekstern_id"), row.string("oppgavestatus")) }
                    .asList
            )
        }
    }

    private fun hentFeltVerdierFraPartisjonertTabell(eksternId: String): List<Pair<String, String>> {
        return get<TransactionalManager>().transaction { tx ->
            tx.run(
                kotliquery.queryOf(
                    """
                    SELECT feltdefinisjon_ekstern_id, verdi FROM oppgavefelt_verdi_part
                     INNER JOIN oppgave_id_part ON oppgave_id = oppgave_id_part.id
                    WHERE oppgave_id_part.oppgave_ekstern_id = :eksternId
                    """.trimIndent(),
                    mapOf("eksternId" to eksternId)
                ).map { row -> Pair(row.string("feltdefinisjon_ekstern_id"), row.string("verdi")) }
                    .asList
            )
        }
    }
}