package no.nav.k9.los.oppgaveuthenting.query.db

import no.nav.k9.los.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgaveuthenting.query.dto.felter.Oppgavefelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.Aggregeringsfunksjon
import no.nav.k9.los.oppgaveuthenting.query.dto.query.AggregertSelectFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.oppgaveuthenting.query.mapping.CombineOperator
import no.nav.k9.los.oppgaveuthenting.query.mapping.FeltverdiOperator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PartisjonertOppgaveQuerySqlBuilderTest {

    private val mockFelter = mapOf(
        OmrådeOgKode(Områder.AKTIVITETSPENGER, "testfelt") to OppgavefeltMedMer(
            Oppgavefelt(
                område = Områder.AKTIVITETSPENGER,
                kode = "testfelt",
                visningsnavn = "Test Felt",
                tolkes_som = "String",
                synlighet = Synlighet.UNDER_STREKEN,
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
            now = LocalDateTime.now(),
            ferdigstiltDatoFilter = null,
            område = Områder.K9
        )

        builder.medFeltverdi(
            CombineOperator.AND,
            Områder.AKTIVITETSPENGER,
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
            now = LocalDateTime.now(),
            ferdigstiltDatoFilter = null,
            område = Områder.K9
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
            now = LocalDateTime.now(),
            ferdigstiltDatoFilter = null,
            område = Områder.K9
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
            now = LocalDateTime.now(),
            ferdigstiltDatoFilter = null,
            område = Områder.K9
        )

        builder.medEnkelOrder(Områder.AKTIVITETSPENGER, "testfelt", true)
        val sql = builder.getQuery()

        assertTrue(sql.contains("SELECT ov.verdi"), "SQL burde inneholde korrekt sorteringsuttrykk")
        assertTrue(sql.contains("ASC"), "SQL burde inneholde stigende sortering")
    }
    
    @Test
    fun `bygger korrekt for kompleks spørring`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER),
            now = LocalDateTime.now(),
            ferdigstiltDatoFilter = null,
            område = Områder.K9
        )

        builder.medFeltverdi(
            CombineOperator.AND,
            Områder.AKTIVITETSPENGER,
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
        
        builder.medEnkelOrder(Områder.AKTIVITETSPENGER, "testfelt", false)
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
            now = LocalDateTime.now(),
            ferdigstiltDatoFilter = null,
            område = Områder.K9
        )
        
        builder.medAggregering(emptyList(), listOf(AggregertSelectFelt(Aggregeringsfunksjon.ANTALL)))
        builder.medFeltverdi(
            CombineOperator.AND,
            Områder.AKTIVITETSPENGER,
            "testfelt", 
            FeltverdiOperator.EQUALS,
            listOf("testverdi")
        )
        
        val sql = builder.getQuery()
        
        assertTrue(sql.contains("COUNT(*)"), "SQL burde telle rader")
    }

    @Test
    fun `medSelectFelter inkluderer ferdigstiltDato`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.LUKKET),
            now = LocalDateTime.now(),
            ferdigstiltDatoFilter = null,
            område = Områder.K9
        )

        builder.medSelectFelter(listOf(
            EnkelSelectFelt(område = null, kode = "ferdigstiltDato")
        ))

        val sql = builder.getQuery()

        assertTrue(sql.contains("o.ferdigstilt_dato"), "SQL burde inneholde o.ferdigstilt_dato som select-felt")
    }

    @Test
    fun `medEnkelOrder håndterer ferdigstiltDato`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.LUKKET),
            now = LocalDateTime.now(),
            ferdigstiltDatoFilter = null,
            område = Områder.K9
        )

        builder.medEnkelOrder(null, "ferdigstiltDato", false)

        val sql = builder.getQuery()

        assertTrue(sql.contains("o.ferdigstilt_dato"), "SQL burde inneholde o.ferdigstilt_dato i ORDER BY")
        assertTrue(sql.contains("DESC"), "SQL burde inneholde synkende sortering")
    }

    @Test
    fun `filtrerer alltid på område, og binder det som parameter`() {
        val builder = PartisjonertOppgaveQuerySqlBuilder(
            felter = mockFelter,
            oppgavestatusFilter = listOf(Oppgavestatus.AAPEN),
            now = LocalDateTime.now(),
            ferdigstiltDatoFilter = null,
            område = Områder.AKTIVITETSPENGER
        )

        val sql = builder.getQuery()

        assertTrue(
            sql.contains("o.omrade_ekstern_id = :omrade"),
            "Spørringen må filtrere på område, ellers lekker oppgaver på tvers av områder"
        )
        assertTrue(
            sql.contains("opc.kildeomrade = :omrade"),
            "Pep-cache-joinen må følge samme område som oppgaven, ikke være hardkodet"
        )
        assertEquals("AKTIVITETSPENGER", builder.getParams()["omrade"])
    }
}
