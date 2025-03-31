package no.nav.k9.los.nyoppgavestyring.query.db

import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PartisjonertOppgaveQuerySqlBuilderTest {

    private val mockFelter = mapOf(
        OmrådeOgKode("TEST", "testfelt") to OppgavefeltMedMer(
            Oppgavefelt(
                område = "TEST",
                kode = "testfelt",
                visningsnavn = "Test Felt",
                tolkes_som = "String",
                kokriterie = false,
                verdiforklaringerErUttømmende = false,
                verdiforklaringer = emptyList()
            ),
            null
        )
    )

    @Test
    fun `bygger korrekt sql for enkel spørring`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.AAPEN),
            now = LocalDateTime.now()
        )

        builder.medFeltverdi(
            CombineOperator.AND,
            "TEST",
            "testfelt", 
            FeltverdiOperator.EQUALS,
            listOf("testverdi")
        )

        val sql = builder.unsafeDebug()

        assertTrue(sql.contains("EXISTS"), "SQL burde inneholde EXISTS-betingelse")
        assertTrue(sql.contains("testfelt"), "SQL burde inneholde feltnavnet")
        assertTrue(sql.contains("oppgavestatus IN"), "SQL burde filtrere på oppgavestatus")
    }

    @Test
    fun `bygger korrekt sql for personbeskyttelse`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.AAPEN),
            now = LocalDateTime.now()
        )

        builder.medFeltverdi(
            CombineOperator.AND,
            null,
            "personbeskyttelse",
            FeltverdiOperator.EQUALS,
            listOf(PersonBeskyttelseType.KODE6.kode)
        )

        val sql = builder.getQuery()

        assertTrue(sql.contains("opc.kode6"), "SQL burde inneholde korrekt PEP-betingelse")
    }

    @Test
    fun `setter paging riktig`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.AAPEN),
            now = LocalDateTime.now()
        )

        builder.medPaging(10, 20)
        val sql = builder.getQuery()

        assertTrue(sql.contains("LIMIT 10 OFFSET 20"), "SQL burde inneholde korrekt paging")
    }
    
    @Test
    fun `håndterer sortering riktig`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.AAPEN),
            now = LocalDateTime.now()
        )

        builder.medEnkelOrder("TEST", "testfelt", true)
        val sql = builder.getQuery()

        assertTrue(sql.contains("SELECT ov.verdi"), "SQL burde inneholde korrekt sorteringsuttrykk")
        assertTrue(sql.contains("ASC"), "SQL burde inneholde stigende sortering")
    }
    
    @Test
    fun `bygger korrekt for kompleks spørring`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER),
            now = LocalDateTime.now()
        )

        builder.medFeltverdi(
            CombineOperator.AND,
            "TEST",
            "testfelt", 
            FeltverdiOperator.EQUALS,
            listOf("testverdi")
        )
        
        builder.medFeltverdi(
            CombineOperator.AND,
            null,
            "oppgavetype",
            FeltverdiOperator.IN,
            listOf("type1", "type2")
        )
        
        builder.medEnkelOrder("TEST", "testfelt", false)
        builder.medPaging(100, 0)
        
        val sql = builder.getQuery()
        val params = builder.getParams()
        
        assertTrue(sql.contains("EXISTS"), "SQL burde inneholde EXISTS-betingelse")
        assertTrue(sql.contains("oppgavetype_ekstern_id"), "SQL burde filtrere på oppgavetype")
        assertTrue(sql.contains("DESC"), "SQL burde inneholde synkende sortering")
        assertTrue(sql.contains("LIMIT 100"), "SQL burde inneholde korrekt paging")
        assertTrue(params.containsKey("feltverdi0"), "Params burde inneholde parametere for filterverdier")
    }
    
    @Test
    fun `genererer sql for telling`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.AAPEN),
            now = LocalDateTime.now()
        )
        
        builder.medAntallSomResultat()
        builder.medFeltverdi(
            CombineOperator.AND,
            "TEST",
            "testfelt", 
            FeltverdiOperator.EQUALS,
            listOf("testverdi")
        )
        
        val sql = builder.getQuery()
        
        assertTrue(sql.contains("SELECT COUNT(*) as antall"), "SQL burde telle rader")
    }
}