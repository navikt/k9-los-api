package no.nav.k9.los.nyoppgavestyring.query

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotEmpty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.mockk
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.kodeverk.BeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.pep.PepCache
import no.nav.k9.los.nyoppgavestyring.pep.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.pep.TestRepository
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefeltMedMer
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgaveQueryToSqlMapper
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.get
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class OppgaveQueryTest : AbstractK9LosIntegrationTest() {

    val logger: Logger = LoggerFactory.getLogger(OppgaveQueryTest::class.java)

    @Test
    fun `sjekker at oppgave-query kan kjøres mot database`() {
        OppgaveTestDataBuilder()
        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val oppgaveQuery = OppgaveQuery(listOf(
            FeltverdiOppgavefilter(null, "oppgavestatus", "EQUALS", listOf("OPPR")),
            FeltverdiOppgavefilter(null, "kildeområde", "EQUALS", listOf("K9")),
            FeltverdiOppgavefilter(null, "oppgavetype", "EQUALS", listOf("aksjonspunkt")),
            FeltverdiOppgavefilter(null, "oppgaveområde", "EQUALS", listOf("aksjonspunkt")),
            FeltverdiOppgavefilter("K9", "fagsystem", "NOT_EQUALS", listOf("Tullball")),
            CombineOppgavefilter("OR", listOf(
                FeltverdiOppgavefilter("K9", "helautomatiskBehandlet", "NOT_EQUALS", listOf("false")),
                FeltverdiOppgavefilter("K9", "mottattDato", "LESS_THAN", listOf(LocalDate.of(2022, 1, 1))),
                CombineOppgavefilter("AND", listOf(
                    FeltverdiOppgavefilter("K9", "totrinnskontroll", "EQUALS", listOf("true")),
                ))
            ))
        ))

        // Verifiserer at det ikke blir exceptions ved serialisering + deserialisering:
        val om = ObjectMapper().dusseldorfConfigured()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerKotlinModule()
        val sw = StringWriter()
        om.writeValue(sw, oppgaveQuery)
        val json = sw.toString()
        om.readValue(json, OppgaveQuery::class.java)

        val result = oppgaveQueryRepository.query(oppgaveQuery)
        assertThat(result).isEmpty()
    }

    @Test
    fun `sjekker at oppgave-query kan utvides ved flere verdier i filter`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "5016")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val oppgaveQuery = OppgaveQuery(listOf(
            byggFilterK9(FeltType.AKSJONSPUNKT, FeltverdiOperator.EQUALS, "5016")
        ))

        val om = ObjectMapper().dusseldorfConfigured()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerKotlinModule()
        val sw = StringWriter()
        om.writeValue(sw, oppgaveQuery)

        val result = oppgaveQueryRepository.query(oppgaveQuery)
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `sjekker at oppgave-query kan sammenligne timestamp`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T00:00:00.000")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.GREATER_THAN, "2023-05-14T00:00:00.000"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.LESS_THAN, "2023-05-15T00:00:00.000"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.GREATER_THAN, "2023-05-15T00:00:00.000"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.LESS_THAN, "2023-05-16T00:00:00.000"),
        )))).isNotEmpty()
    }

    @Test
    fun `sjekker at oppgave-query kan sjekke timestamp for likhet - urealistisk med timestamp`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T00:00:00.000")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.LESS_THAN_OR_EQUALS, "2023-05-15T00:00:00.000"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.EQUALS, "2023-05-15T00:00:00.000"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.GREATER_THAN_OR_EQUALS, "2023-05-15T00:00:00.000"),
        )))).isNotEmpty()
    }


    @Test
    fun `sjekker at oppgave-query med kun dato kan sjekkes mot timestamp`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T00:00:00.000")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.LESS_THAN_OR_EQUALS, "2023-05-16"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.EQUALS, "2023-05-15"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.GREATER_THAN_OR_EQUALS, "2023-05-14"),
        )))).isNotEmpty()
    }

    @Test
    fun `sjekker at oppgave-query med boolean kan ha null`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .lagOgLagre()   // avventerArbeidsgiver er null

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.EQUALS, null),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.IN, null),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.IN, null, "true"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.IN, "true"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.EQUALS, "true"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.EQUALS, "false"),
        )))).isEmpty()
    }

    @Test
    fun `sjekker at oppgave-query med boolean med true`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.AVVENTER_ARBEIDSGIVER, "true")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.EQUALS, null),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.IN, null),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.IN, null, "true"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.EQUALS, "true"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.IN, "true"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.AVVENTER_ARBEIDSGIVER, FeltverdiOperator.EQUALS, "false"),
        )))).isEmpty()
    }

    @Test
    fun `sjekker at oppgave-query med flere datoer som ikke skal matche`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T12:00:00.000")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.NOT_EQUALS, "2023-05-15T00:00:00.000"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.NOT_EQUALS, "2023-05-15T00:00:00.000", "2023-05-16T00:00:00.000"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.NOT_EQUALS, "2023-05-14T00:00:00.000", "2023-05-16T00:00:00.000"),
        )))).isNotEmpty()
    }

    @Test // Query er ikke ment som tilgangskontroll, men en kjapp måte å utføre filtrering før tilgangssjekk gjøres på resultatet
    fun  `Resultat skal inneholde alle sikkerhetsklassifiseringer når ikke beskyttelse eller egen ansatt er spesifisert i filtre`() {
        val eksternId = lagOppgave(kode6 = true, kode7 = true, egenAnsatt = true)

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val query = OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, eksternId),
        ))

        assertThat(oppgaveQueryRepository.query(query)).isNotEmpty()
    }

    @Test
    fun `Resultat skal kun inneholde ordinære oppgaver når filtre er satt`() {
        val eksternId = lagOppgave()

        loggAlleOppgaverMedFelterOgCache()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val query = OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, eksternId),
            byggGenereltFilter(FeltType.BESKYTTELSE, FeltverdiOperator.IN, BeskyttelseType.ORDINÆR.kode)
        ))

        assertThat(oppgaveQueryRepository.query(query)).isNotEmpty()
    }

    @Test
    @Disabled
    fun `Resultat skal kun inneholde kode6-oppgaver når filtre er satt til kode6`() {
        val eksternId = lagOppgave(kode6 = true)

        loggAlleOppgaverMedFelterOgCache()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val query = OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, eksternId),
            byggGenereltFilter(FeltType.BESKYTTELSE, FeltverdiOperator.IN, "KODE6")
        ))

        assertThat(oppgaveQueryRepository.query(query)).isNotEmpty()
    }

    @Test
    fun `Resultat skal ikke inneholde kode6- eller kode7oppgaver når filtre er satt til ordinære oppgaver`() {
        val eksternId6 = lagOppgave(kode6 = true)
        val eksternId7 = lagOppgave(kode7 = true)

        loggAlleOppgaverMedFelterOgCache()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val query = OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, eksternId6, eksternId7),
            byggGenereltFilter(FeltType.BESKYTTELSE, FeltverdiOperator.IN, BeskyttelseType.ORDINÆR.kode)
        ))

        assertThat(oppgaveQueryRepository.query(query)).isEmpty()
    }

    @Test
    fun `ytelse - oppgavequery uten filter på oppgavestatus må ha param for oppgavequery for bruk av index i oppgavefelt_verdi`() {
        OppgaveTestDataBuilder()
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(mockk(), mockk<FeltdefinisjonRepository>())

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.LESS_THAN_OR_EQUALS, "2023-05-16"),
            )
        )

        val områdeOgKodeOppgavefeltMedMerMap = mapOf(
            Pair(
                OmrådeOgKode("K9", FeltType.MOTTATT_DATO.eksternId),
                OppgavefeltMedMer(
                    Oppgavefelt(
                        område = "K9",
                        kode = FeltType.MOTTATT_DATO.eksternId,
                        "dette er en test",
                        tolkes_som = Datatype.TIMESTAMP.kode,
                        kokriterie = false,
                        verdiforklaringerErUttømmende = false,
                        verdiforklaringer = null
                    ),
                    null
                )
            )
        )

        val sqlOppgaveQuery = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(oppgaveQuery, områdeOgKodeOppgavefeltMedMerMap, LocalDateTime.now())

        assertThat(sqlOppgaveQuery.getParams().get("oppgavestatus")).isEqualTo(listOf(Oppgavestatus.entries))
    }

    @Test
    @Disabled
    fun `Resultat skal ikke inneholde kode7 eller ordinære oppgaver når filtre er satt til kode6 oppgaver`() {
        val eksternId7 = lagOppgave(kode7 = true)
        val eksternIdOrdinær = lagOppgave()

        loggAlleOppgaverMedFelterOgCache()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val query = OppgaveQuery(listOf(
            byggFilterK9(FeltType.BEHANDLINGUUID, FeltverdiOperator.EQUALS, eksternId7, eksternIdOrdinær),
            byggGenereltFilter(FeltType.BESKYTTELSE, FeltverdiOperator.IN, "KODE6")
        ))

        assertThat(oppgaveQueryRepository.query(query)).isEmpty()
    }



    private fun lagOppgave(
        kode6: Boolean = false,
        kode7: Boolean = false,
        egenAnsatt: Boolean = false
    ): String {
        val eksternId = UUID.randomUUID().toString()
        lagPepCacheFor(eksternId, kode6, kode7, egenAnsatt)

        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, eksternId)
            .lagOgLagre()
        return eksternId
    }

    private fun lagPepCacheFor(
        eksternId: String,
        kode6: Boolean = false,
        kode7: Boolean = false,
        egenAnsatt: Boolean = false
    ) {
        val pepCache = get<PepCacheRepository>()
        val transactionalManager = get<TransactionalManager>()
        transactionalManager.transaction { tx ->
            pepCache.lagre(
                PepCache(
                    eksternId = eksternId,
                    kildeområde = "K9",
                    kode6 = kode6,
                    kode7 = kode7,
                    egenAnsatt = egenAnsatt,
                    oppdatert = LocalDateTime.now()
                ), tx
            )
        }
    }


    private fun byggFilterK9(feltType: FeltType, feltverdiOperator: FeltverdiOperator, vararg verdier: String?): FeltverdiOppgavefilter {
        return FeltverdiOppgavefilter(
            "K9",
            feltType.eksternId,
            feltverdiOperator.name,
            verdier.toList()
        )
    }

    private fun byggGenereltFilter(feltType: FeltType, feltverdiOperator: FeltverdiOperator, vararg verdier: String?): FeltverdiOppgavefilter {
        return FeltverdiOppgavefilter(
            null,
            feltType.eksternId,
            feltverdiOperator.name,
            verdier.toList()
        )
    }

    @Test
    fun `sjekker at oppgave-query kan deserialiseres med feltverdi som ikke er array`() {
        val json = """
            {
              "filtere" : [{
                "type" : "feltverdi",
                "område" : null,
                "kode" : "oppgavestatus",
                "operator" : "EQUALS",
                "verdi" : "OPPR"
              }],
              "select" : [ ],
              "order" : [ ],
              "limit" : 10
            }
        """.trimIndent()

        val om = ObjectMapper().dusseldorfConfigured()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerKotlinModule()

        val objectAgain: OppgaveQuery = om.readValue(json, OppgaveQuery::class.java)
        assertThat(objectAgain).isNotNull()
    }

    private fun loggAlleOppgaverMedFelterOgCache() {

        val oppgaveRepository = get<OppgaveRepository>()
        val testRepository = TestRepository()
        val pepCache = get<PepCacheRepository>()

        val transactionalManager = get<TransactionalManager>()
        transactionalManager.transaction { tx ->
            testRepository.hentEksternIdForAlleOppgaver(tx).forEach { eksternId ->
                logger.info("eksternId $eksternId")
                logger.info("Oppgave: "+oppgaveRepository.hentNyesteOppgaveForEksternId(tx, "K9", eksternId).felter.joinToString(", ") { it.eksternId + "-" + it.verdi })
                logger.info("Pep: "+pepCache.hent("K9", eksternId, tx)?.run { "kode6-$kode6, kode7-$kode7, egenansatt-$egenAnsatt, oppdater-$oppdatert" })
            }
        }
    }

}