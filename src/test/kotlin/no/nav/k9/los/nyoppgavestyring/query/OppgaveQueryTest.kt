package no.nav.k9.los.nyoppgavestyring.query

import assertk.assertThat
import assertk.assertions.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.TestRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.dto.query.*
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.OppgaveQueryResultat
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        val oppgaveQuery = OppgaveQuery(
            listOf(
                FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.AAPEN.kode)),
                FeltverdiOppgavefilter(null, "kildeområde", EksternFeltverdiOperator.EQUALS, listOf("K9")),
                FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("aksjonspunkt")),
                FeltverdiOppgavefilter(null, "oppgaveområde", EksternFeltverdiOperator.EQUALS, listOf("aksjonspunkt")),
                FeltverdiOppgavefilter("K9", "fagsystem", EksternFeltverdiOperator.NOT_EQUALS, listOf("Tullball")),
                CombineOppgavefilter(
                    CombineOperator.OR, listOf(
                        FeltverdiOppgavefilter("K9", "helautomatiskBehandlet", EksternFeltverdiOperator.NOT_EQUALS, listOf("false")),
                        FeltverdiOppgavefilter("K9", "mottattDato", EksternFeltverdiOperator.LESS_THAN, listOf(LocalDate.of(2022, 1, 1))),
                        CombineOppgavefilter(
                            CombineOperator.AND, listOf(
                                FeltverdiOppgavefilter("K9", "totrinnskontroll", EksternFeltverdiOperator.EQUALS, listOf("true")),
                            )
                        )
                    )
                )
            )
        )

        // Verifiserer at det ikke blir exceptions ved serialisering + deserialisering:
        val om = ObjectMapper().dusseldorfConfigured()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerKotlinModule()
        val sw = StringWriter()
        om.writeValue(sw, oppgaveQuery)
        val json = sw.toString()
        om.readValue(json, OppgaveQuery::class.java)

        val result = queryForOppgave(QueryRequest(oppgaveQuery))
        assertThat(result).isEmpty()
    }

    @Test
    fun `sjekker at oppgave-query kan utvides ved flere verdier i filter`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "5016")
            .lagOgLagre()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilter(FeltType.AKSJONSPUNKT, EksternFeltverdiOperator.EQUALS, "5016")
            )
        )

        val result = queryForOppgave(QueryRequest(oppgaveQuery))
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `sjekker at oppgave-query kan sammenligne timestamp`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T00:00:00.000")
            .lagOgLagre()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(
                                FeltType.MOTTATT_DATO,
                                EksternFeltverdiOperator.GREATER_THAN,
                                "2023-05-14T00:00:00.000"
                            ),
                        )
                    )
                )
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.MOTTATT_DATO, EksternFeltverdiOperator.LESS_THAN, "2023-05-15T00:00:00.000"),
                        )
                    )
                )
            )
        ).isEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(
                                FeltType.MOTTATT_DATO,
                                EksternFeltverdiOperator.GREATER_THAN,
                                "2023-05-15T00:00:00.000"
                            ),
                        )
                    )
                )
            )
        ).isEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.MOTTATT_DATO, EksternFeltverdiOperator.LESS_THAN, "2023-05-16T00:00:00.000"),
                        )
                    )
                )
            )
        ).isNotEmpty()
    }

    @Test
    fun `sjekker at oppgave-query kan sjekke timestamp for likhet - urealistisk med timestamp`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T00:00:00.000")
            .lagOgLagre()


        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(
                                FeltType.MOTTATT_DATO,
                                EksternFeltverdiOperator.LESS_THAN_OR_EQUALS,
                                "2023-05-15T00:00:00.000"
                            ),
                        )
                    )
                )
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.MOTTATT_DATO, EksternFeltverdiOperator.EQUALS, "2023-05-15T00:00:00.000"),
                        )
                    )
                )
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(
                                FeltType.MOTTATT_DATO,
                                EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                                "2023-05-15T00:00:00.000"
                            ),
                        )
                    )
                )
            )
        ).isNotEmpty()
    }


    @Test
    fun `sjekker at oppgave-query med kun dato kan sjekkes mot timestamp`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T00:00:00.000")
            .lagOgLagre()


        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.MOTTATT_DATO, EksternFeltverdiOperator.LESS_THAN_OR_EQUALS, "2023-05-16"),
                        )
                    )
                )
            )
        ).isNotEmpty()

        val request = QueryRequest(
            OppgaveQuery(
                listOf(
                    byggFilter(FeltType.MOTTATT_DATO, EksternFeltverdiOperator.EQUALS, "2023-05-15"),
                )
            )
        )
        assertThat(
            queryForOppgave(
                request
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.MOTTATT_DATO, EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, "2023-05-14"),
                        )
                    )
                )
            )
        ).isNotEmpty()
    }

    @Test
    fun `sjekker at oppgave-query med boolean kan ha null`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .lagOgLagre()   // avventerArbeidsgiver er null


        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.EQUALS, null),
                        )
                    )
                )
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.IN, null),
                        )
                    )
                )
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.IN, null, "true"),
                        )
                    )
                )
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.IN, "true"),
                        )
                    )
                )
            )
        ).isEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.EQUALS, "true"),
                        )
                    )
                )
            )
        ).isEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.EQUALS, "false"),
                        )
                    )
                )
            )
        ).isEmpty()
    }

    @Test
    fun `sjekker at oppgave-query med boolean med true`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.AVVENTER_ARBEIDSGIVER, "true")
            .lagOgLagre()


        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.EQUALS, null),
                        )
                    )
                )
            )
        ).isEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.IN, null),
                        )
                    )
                )
            )
        ).isEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.IN, null, "true"),
                        )
                    )
                )
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.EQUALS, "true"),
                        )
                    )
                )
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.IN, "true"),
                        )
                    )
                )
            )
        ).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(FeltType.AVVENTER_ARBEIDSGIVER, EksternFeltverdiOperator.EQUALS, "false"),
                        )
                    )
                )
            )
        ).isEmpty()
    }

    @Test
    fun `sjekker at oppgave-query med flere datoer som ikke skal matche`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T12:00:00.000")
            .lagOgLagre()


        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(
                                FeltType.MOTTATT_DATO,
                                EksternFeltverdiOperator.NOT_EQUALS,
                                "2023-05-15"
                            ),
                        )
                    )
                )
            )
        ).isEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(
                                FeltType.MOTTATT_DATO,
                                EksternFeltverdiOperator.NOT_EQUALS,
                                "2023-05-15",
                                "2023-05-16"
                            ),
                        )
                    )
                )
            )
        ).isEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, behandlingUuid),
                            byggFilter(
                                FeltType.MOTTATT_DATO,
                                EksternFeltverdiOperator.NOT_EQUALS,
                                "2023-05-14T00:00:00.000",
                                "2023-05-16T00:00:00.000"
                            ),
                        )
                    )
                )
            )
        ).isNotEmpty()
    }

    @Test
    fun `Filtere uten feltverdier skal ha samme resultat som om filtret ikke er brukt`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, behandlingUuid)
            .lagOgLagre()


        assertThat(queryForOppgave(QueryRequest(OppgaveQuery(listOf())))).isNotEmpty()

        assertThat(
            queryForOppgave(
                QueryRequest(
                    OppgaveQuery(
                        listOf(
                            byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.IN),
                        )
                    )
                )
            )
        ).isNotEmpty()
    }

    @Test
    fun `sjekker at ekskluderer-verdi-query finner oppgaver som ikke har feltet`() {
        val testbuilder = OppgaveTestDataBuilder()
        testbuilder.medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, UUID.randomUUID().toString())
            //denne har IKKE fagsystem
            .lagOgLagre()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                FeltverdiOppgavefilter("K9", "fagsystem", EksternFeltverdiOperator.NOT_EQUALS, listOf("K9PUNSJ"))
            )
        )

        val result = queryForOppgave(QueryRequest(oppgaveQuery))
        assertThat(result).hasSize(1)
    }

    @Test
    fun `sjekker at ekskluderer-verdi-query finner oppgaver som har feltet men annen verdi enn angitt`() {
        val testbuilder = OppgaveTestDataBuilder()
        testbuilder.medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, UUID.randomUUID().toString())
            .medOppgaveFeltVerdi(FeltType.FAGSYSTEM, "K9SAK")
            .lagOgLagre()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                FeltverdiOppgavefilter("K9", "fagsystem", EksternFeltverdiOperator.NOT_EQUALS, listOf("K9PUNSJ"))
            )
        )

        val result = queryForOppgave(QueryRequest(oppgaveQuery))
        assertThat(result).hasSize(1)
    }

    @Test
    fun `sjekker at ekskluderer-verdi-query ikke finner oppgaver som har feltet med angitt verdi`() {
        val testbuilder = OppgaveTestDataBuilder()
        testbuilder.medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, UUID.randomUUID().toString())
            .medOppgaveFeltVerdi(FeltType.FAGSYSTEM, "K9PUNSJ")
            .lagOgLagre()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                FeltverdiOppgavefilter("K9", "fagsystem", EksternFeltverdiOperator.NOT_EQUALS, listOf("K9PUNSJ"))
            )
        )

        val result = queryForOppgave(QueryRequest(oppgaveQuery))
        assertThat(result).isEmpty()
    }

    @Test // Query er ikke ment som tilgangskontroll, men en kjapp måte å utføre filtrering før tilgangssjekk gjøres på resultatet
    fun `Resultat skal inneholde alle sikkerhetsklassifiseringer når ikke beskyttelse eller egen ansatt er spesifisert i filtre`() {
        val eksternId = lagOppgaveMedPepCache(kode6 = true, kode7 = true, egenAnsatt = true)

        val query = OppgaveQuery(
            listOf(
                byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, eksternId),
            )
        )

        assertThat(queryForOppgave(QueryRequest(query))).isNotEmpty()
    }

    @Test // Ikke tilgangskontroll, men kun ment for ytelsesoptimalisering
    fun `Resultat skal inneholde alle resultat uavhengig av forespurt sikkerhetsklassifisering hvis pepcache mangler`() {
        val eksternId = lagOppgave()

        val query = OppgaveQuery(
            listOf(
                byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, eksternId),
                byggFilter(
                    FeltType.PERSONBESKYTTELSE,
                    EksternFeltverdiOperator.EQUALS,
                    PersonBeskyttelseType.KODE7_ELLER_EGEN_ANSATT.kode
                )
            )
        )

        assertThat(queryForOppgave(QueryRequest(query))).isNotEmpty()
    }

    @Test
    fun `Resultat skal kun inneholde ordinære oppgaver når filtre er satt`() {
        val eksternId = lagOppgaveMedPepCache()

        loggAlleOppgaverMedFelterOgCache()

        val query = OppgaveQuery(
            listOf(
                byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, eksternId),
                byggFilter(
                    FeltType.PERSONBESKYTTELSE,
                    EksternFeltverdiOperator.EQUALS,
                    PersonBeskyttelseType.UGRADERT.kode
                )
            )
        )

        assertThat(queryForOppgave(QueryRequest(query))).isNotEmpty()
    }

    @Test
    fun `Resultat skal kun inneholde kode6-oppgaver når filtre er satt til kode6`() {
        val eksternId = lagOppgaveMedPepCache(kode6 = true)

        loggAlleOppgaverMedFelterOgCache()

        val query = OppgaveQuery(
            listOf(
                byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, eksternId),
                byggFilter(
                    FeltType.PERSONBESKYTTELSE,
                    EksternFeltverdiOperator.EQUALS,
                    PersonBeskyttelseType.KODE6.kode
                )
            )
        )

        assertThat(queryForOppgave(QueryRequest(query))).isNotEmpty()
    }

    @Test
    fun `Resultat skal ikke inneholde kode6- eller kode7oppgaver når filtre er satt til ordinære oppgaver`() {
        val eksternId6 = lagOppgaveMedPepCache(kode6 = true)
        val eksternId7 = lagOppgaveMedPepCache(kode7 = true)

        loggAlleOppgaverMedFelterOgCache()

        val query = OppgaveQuery(
            listOf(
                byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, eksternId6, eksternId7),
                byggFilter(
                    FeltType.PERSONBESKYTTELSE,
                    EksternFeltverdiOperator.EQUALS,
                    PersonBeskyttelseType.UGRADERT.kode
                )
            )
        )

        assertThat(queryForOppgave(QueryRequest(query))).isEmpty()
    }

    @Test
    fun `Beslutter-kø skal inneholde oppgaver for k9sak-behandlinger med aksjonspunkt 5016`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, UUID.randomUUID().toString())
            .medOppgaveFeltVerdi(FeltType.LØSBART_AKSJONSPUNKT, "5016")
            .medOppgaveFeltVerdi(FeltType.LIGGER_HOS_BESLUTTER, true.toString())
            .lagOgLagre()

        val query = OppgaveQuery(
            listOf(
                byggFilter(FeltType.LIGGER_HOS_BESLUTTER, EksternFeltverdiOperator.EQUALS, "true"),
                byggFilter(
                    FeltType.PERSONBESKYTTELSE,
                    EksternFeltverdiOperator.EQUALS,
                    PersonBeskyttelseType.UGRADERT.kode
                )
            )
        )
        assertThat(queryForOppgave(QueryRequest(query))).hasSize(1)
    }

    @Test
    fun `Beslutter-kø skal inneholde oppgaver for k9klage-behandlinger med aksjonspunkt 5016`() {
        OppgaveTestDataBuilder(definisjonskilde = "k9-klage-til-los", oppgaveTypeNavn = "k9klage")
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, UUID.randomUUID().toString())
            .medOppgaveFeltVerdi(FeltType.LØSBART_AKSJONSPUNKT, "5016")
            .medOppgaveFeltVerdi(FeltType.LIGGER_HOS_BESLUTTER, true.toString())
            .lagOgLagre()

        val query = OppgaveQuery(
            listOf(
                byggFilter(FeltType.LIGGER_HOS_BESLUTTER, EksternFeltverdiOperator.EQUALS, "true"),
                byggFilter(
                    FeltType.PERSONBESKYTTELSE,
                    EksternFeltverdiOperator.EQUALS,
                    PersonBeskyttelseType.UGRADERT.kode
                )
            )
        )
        assertThat(queryForOppgave(QueryRequest(query))).hasSize(1)
    }

    @Test
    fun `Beslutter-kø for skal inneholde oppgaver for k9-tilbake-behandlinger med aksjonspunkt 5005`() {
        val eksternId = UUID.randomUUID().toString()
        OppgaveTestDataBuilder(definisjonskilde = "k9-tilbake-til-los", oppgaveTypeNavn = "k9tilbake")
            .medOppgaveFeltVerdi(FeltType.BEHANDLINGUUID, eksternId)
            .medOppgaveFeltVerdi(FeltType.LØSBART_AKSJONSPUNKT, "5005")
            .medOppgaveFeltVerdi(FeltType.LIGGER_HOS_BESLUTTER, true.toString())
            .lagOgLagre()

        val query = OppgaveQuery(
            listOf(
                byggFilter(FeltType.LIGGER_HOS_BESLUTTER, EksternFeltverdiOperator.EQUALS, "true"),
                byggFilter(
                    FeltType.PERSONBESKYTTELSE,
                    EksternFeltverdiOperator.EQUALS,
                    PersonBeskyttelseType.UGRADERT.kode
                )
            )
        )
        assertThat(queryForOppgave(QueryRequest(query))).hasSize(1)
    }

    @Test
    fun `queryRequest som vil fjerne reserverte oppgaver skal kun få ureserverte`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val saksbehandler = runBlocking {
            val ident = "test"
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    null,
                    ident,
                    ident,
                    ident + "@nav.no",
                    enhet = "1234"
                )
            )
            saksbehandlerRepository.hentAlleSaksbehandlere()
        }.get(0)

        val builder = OppgaveTestDataBuilder()
        builder.lagOgLagre(Oppgavestatus.AAPEN)
        builder.lagre(builder.lag(reservasjonsnøkkel = "test"))

        val reservasjonstjeneste = get<ReservasjonV3Tjeneste>()
        reservasjonstjeneste.taReservasjon(
            "test",
            saksbehandler.id!!,
            saksbehandler.id!!,
            "test",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(2)
        )

        val query = OppgaveQuery(
            listOf(
                byggFilter(
                    FeltType.OPPGAVE_STATUS,
                    EksternFeltverdiOperator.IN,
                    Oppgavestatus.AAPEN.kode
                )
            )
        )

        val queryService = get<OppgaveQueryService>()

        assertThat(queryService.queryForAntall(QueryRequest(query, fjernReserverte = true))).isEqualTo(1)

        assertThat(queryService.queryForAntall(QueryRequest(query, fjernReserverte = false))).isEqualTo(2)
    }

    @Test
    fun `queryRequest limit og paginering`() {
        val builder = OppgaveTestDataBuilder()
        val oppgave1 = builder.lagOgLagre(Oppgavestatus.AAPEN)
        val oppgave2 = builder.lagOgLagre(Oppgavestatus.AAPEN)

        val query = OppgaveQuery(
            filtere = listOf(
                byggFilter(
                    FeltType.OPPGAVE_STATUS,
                    EksternFeltverdiOperator.IN,
                    Oppgavestatus.AAPEN.kode
                )
            ),
            order = listOf(
                EnkelOrderFelt(område = "K9", kode = FeltType.MOTTATT_DATO.eksternId, økende = true)
            )
        )

        val queryService = get<OppgaveQueryService>()

        assertThat(queryService.queryForAntall(QueryRequest(query))).isEqualTo(2)

        assertThat(
            queryService.queryForOppgave(
                QueryRequest(
                    query,
                    avgrensning = Avgrensning.maxAntall(1)
                )
            ).size
        ).isEqualTo(1)

        assertThat(queryService.queryForAntall(QueryRequest(query))).isEqualTo(2)

        val søk1 = queryService.queryForOppgaveEksternId(QueryRequest(query, avgrensning = Avgrensning.paginert(1, 1)))
        val søk2 = queryService.queryForOppgaveEksternId(QueryRequest(query, avgrensning = Avgrensning.paginert(1, 2)))

        assertThat(søk1.size).isEqualTo(1)
        assertThat(søk2.size).isEqualTo(1)

        assertThat(søk1.get(0)).isNotEqualTo(søk2.get(0))
        assertThat(søk1.get(0).eksternId).isIn(oppgave1.eksternId, oppgave2.eksternId)
        assertThat(søk2.get(0).eksternId).isIn(oppgave1.eksternId, oppgave2.eksternId)
    }

    @Test
    fun `kan finne lukkede oppgaver, fra partisjonert tabell`() {
        OppgaveTestDataBuilder()
            .lagOgLagre()

        OppgaveTestDataBuilder()
            .lagOgLagre(Oppgavestatus.LUKKET)

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.IN, Oppgavestatus.LUKKET.kode),
            )
        )

        assertThat(queryForOppgave(QueryRequest(oppgaveQuery)).size).isEqualTo(1)
    }

    @Test
    fun `oppgavequery med filter på oppgavestatus skal håndtere alle statuser`() {
        OppgaveTestDataBuilder()
            .lagOgLagre()

        OppgaveTestDataBuilder()
            .lagOgLagre(Oppgavestatus.LUKKET)


        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.IN, Oppgavestatus.AAPEN.kode),
            )
        )

        assertThat(queryForOppgave(QueryRequest(oppgaveQuery)).size).isEqualTo(1)

        val oppgaveQuery2 = OppgaveQuery(
            listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.IN, Oppgavestatus.VENTER.kode),
            )
        )

        assertThat(queryForOppgave(QueryRequest(oppgaveQuery2)).size).isEqualTo(0)

        val oppgaveQuery4 = OppgaveQuery(
            listOf(
            )
        )

        assertThat(queryForOppgave(QueryRequest(oppgaveQuery4)).size).isEqualTo(2)

        val oppgaveQuery5 = OppgaveQuery(
            listOf(
                byggFilter(
                    FeltType.OPPGAVE_STATUS,
                    EksternFeltverdiOperator.IN,
                    Oppgavestatus.LUKKET.kode,
                    Oppgavestatus.AAPEN.kode
                ),
            )
        )

        assertThat(queryForOppgave(QueryRequest(oppgaveQuery5)).size).isEqualTo(2)
    }

    @Test
    fun `Resultat skal ikke inneholde kode7 eller ordinære oppgaver når filtre er satt til kode6 oppgaver`() {
        val eksternId7 = lagOppgaveMedPepCache(kode7 = true)
        val eksternIdOrdinær = lagOppgaveMedPepCache()

        loggAlleOppgaverMedFelterOgCache()

        val query = OppgaveQuery(
            listOf(
                byggFilter(FeltType.BEHANDLINGUUID, EksternFeltverdiOperator.EQUALS, eksternId7, eksternIdOrdinær),
                byggFilter(
                    FeltType.PERSONBESKYTTELSE,
                    EksternFeltverdiOperator.EQUALS,
                    PersonBeskyttelseType.KODE6.kode
                )
            )
        )

        assertThat(queryForOppgave(QueryRequest(query))).isEmpty()
    }

    @Test
    fun `sjekk at ferdigstiltDato kan brukes som felt`() {
        val oppgaveTestDataBuilder = OppgaveTestDataBuilder()

        // lager to oppgaver, en ferdigstilt i dag og en ferdigstilt 1. januar 2025
        oppgaveTestDataBuilder
            .lagOgLagre(
                status = Oppgavestatus.LUKKET,
            )
        val oppgaveLukketFørsteJanuar = oppgaveTestDataBuilder
            .lagOgLagre(
                status = Oppgavestatus.LUKKET,
                endretTidspunkt = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
            )

        val resultat = executeQuery(
            QueryRequest(
                OppgaveQuery(
                    filtere = listOf(
                        byggFilter(FeltType.FERDIGSTILT_DATO, EksternFeltverdiOperator.EQUALS, "2025-01-01"),
                        byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.EQUALS, "LUKKET"),
                    ),
                    select = listOf(EksternIdSelectFelt),
                )
            ), LocalDateTime.now())
        resultat as OppgaveQueryResultat.EksternIdResultat
        assertThat(resultat.ider.map { it.eksternId }).containsOnly(oppgaveLukketFørsteJanuar.eksternId)
    }

    @Test
    fun `sjekk at ferdigstiltDato returneres som select-felt i uttrekk`() {
        val oppgaveTestDataBuilder = OppgaveTestDataBuilder()

        oppgaveTestDataBuilder.lagOgLagre(
            status = Oppgavestatus.LUKKET,
            endretTidspunkt = LocalDateTime.of(2025, 3, 15, 0, 0, 0)
        )

        val resultat = executeQuery(
            QueryRequest(
                OppgaveQuery(
                    filtere = listOf(
                        byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.EQUALS, "LUKKET"),
                    ),
                    select = listOf(
                        EnkelSelectFelt(område = null, kode = "ferdigstiltDato"),
                    ),
                )
            )
        )

        assertThat(resultat).isInstanceOf(OppgaveQueryResultat.SelectResultat::class.java)
        resultat as OppgaveQueryResultat.SelectResultat
        val ferdigstiltVerdi = resultat.rader.first().felter.first { it.kode == "ferdigstiltDato" }.verdi
        assertThat(ferdigstiltVerdi).isNotNull()
        assertThat(ferdigstiltVerdi).isEqualTo("2025-03-15")
    }


    @Test
    fun `queryForGruppering grupperer oppgaver på angitt felt`() {
        val builder = OppgaveTestDataBuilder()
        builder.medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "BT-002").lagOgLagre()
        builder.medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "BT-002").lagOgLagre()
        builder.medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "BT-004").lagOgLagre()

        val query = OppgaveQuery(
            filtere = listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.IN, Oppgavestatus.AAPEN.kode)
            ),
            select = listOf(
                EnkelSelectFelt("K9", "behandlingTypekode"),
                AggregertSelectFelt(Aggregeringsfunksjon.ANTALL),
            ),
        )

        val queryService = get<OppgaveQueryService>()
        val resultat = queryService.query(QueryRequest(query))

        assertThat(resultat).isInstanceOf(OppgaveQueryResultat.GruppertResultat::class.java)
        val grupper = (resultat as OppgaveQueryResultat.GruppertResultat).rader
        assertThat(grupper).hasSize(2)
        val bt002 = grupper.first { it.grupperingsverdier.first().verdi == "BT-002" }
        val bt004 = grupper.first { it.grupperingsverdier.first().verdi == "BT-004" }
        assertThat(bt002.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi).isEqualTo("2")
        assertThat(bt004.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi).isEqualTo("1")
    }

    @Test
    fun `queryForGruppering på felt uten område`() {
        val builder = OppgaveTestDataBuilder()
        builder.lagOgLagre(Oppgavestatus.AAPEN)
        builder.lagOgLagre(Oppgavestatus.AAPEN)
        builder.lagOgLagre(Oppgavestatus.VENTER)

        val query = OppgaveQuery(
            filtere = listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.IN, Oppgavestatus.AAPEN.kode, Oppgavestatus.VENTER.kode)
            ),
            select = listOf(
                EnkelSelectFelt(null, "oppgavestatus"),
                AggregertSelectFelt(Aggregeringsfunksjon.ANTALL),
            ),
        )

        val queryService = get<OppgaveQueryService>()
        val resultat = queryService.query(QueryRequest(query))

        assertThat(resultat).isInstanceOf(OppgaveQueryResultat.GruppertResultat::class.java)
        val grupper = (resultat as OppgaveQueryResultat.GruppertResultat).rader
        assertThat(grupper).hasSize(2)
        val aapen = grupper.first { it.grupperingsverdier.first().verdi == Oppgavestatus.AAPEN.kode }
        val venter = grupper.first { it.grupperingsverdier.first().verdi == Oppgavestatus.VENTER.kode }
        assertThat(aapen.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi).isEqualTo("2")
        assertThat(venter.aggregeringer.first { it.type == Aggregeringsfunksjon.ANTALL }.verdi).isEqualTo("1")
    }

    @Test
    fun `queryForAntall ignorerer select og order fra gruppert query`() {
        val builder = OppgaveTestDataBuilder()
        builder.medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "BT-002").lagOgLagre()
        builder.medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "BT-002").lagOgLagre()
        builder.medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "BT-004").lagOgLagre()

        val query = OppgaveQuery(
            filtere = listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.IN, Oppgavestatus.AAPEN.kode)
            ),
            select = listOf(
                EnkelSelectFelt("K9", "behandlingTypekode"),
                AggregertSelectFelt(Aggregeringsfunksjon.ANTALL),
            ),
            order = listOf(
                EnkelOrderFelt("K9", "behandlingTypekode", true),
            ),
        )

        val queryService = get<OppgaveQueryService>()
        assertThat(queryService.queryForAntall(QueryRequest(query))).isEqualTo(3L)
    }

    @Test
    fun `queryForGruppering respekterer order på grupperingsfelt`() {
        val builder = OppgaveTestDataBuilder()
        builder.medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "BT-004").lagOgLagre()
        builder.medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "BT-002").lagOgLagre()
        builder.medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "BT-002").lagOgLagre()

        val query = OppgaveQuery(
            filtere = listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.IN, Oppgavestatus.AAPEN.kode)
            ),
            select = listOf(
                EnkelSelectFelt("K9", "behandlingTypekode"),
                AggregertSelectFelt(Aggregeringsfunksjon.ANTALL),
            ),
            order = listOf(
                EnkelOrderFelt("K9", "behandlingTypekode", true),
            ),
        )

        val queryService = get<OppgaveQueryService>()
        val resultat = queryService.query(QueryRequest(query))

        assertThat(resultat).isInstanceOf(OppgaveQueryResultat.GruppertResultat::class.java)
        val grupper = (resultat as OppgaveQueryResultat.GruppertResultat).rader
        assertThat(grupper.map { it.grupperingsverdier.first().verdi }).containsExactly("BT-002", "BT-004")
    }

    @Test
    fun `queryForGruppering aggregerer heltallsfelt datatypebevisst`() {
        val builder = OppgaveTestDataBuilder(definisjonskilde = "k9-tilbake-til-los", oppgaveTypeNavn = "k9tilbake")
        builder.medOppgaveFeltVerdi(FeltType.FEILUTBETALT_BELØP, "100").lagOgLagre()
        builder.medOppgaveFeltVerdi(FeltType.FEILUTBETALT_BELØP, "200").lagOgLagre()

        val query = OppgaveQuery(
            filtere = listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.EQUALS, Oppgavestatus.AAPEN.kode)
            ),
            select = listOf(
                AggregertSelectFelt(Aggregeringsfunksjon.SUM, "K9", FeltType.FEILUTBETALT_BELØP.eksternId),
                AggregertSelectFelt(Aggregeringsfunksjon.GJENNOMSNITT, "K9", FeltType.FEILUTBETALT_BELØP.eksternId),
                AggregertSelectFelt(Aggregeringsfunksjon.MIN, "K9", FeltType.FEILUTBETALT_BELØP.eksternId),
                AggregertSelectFelt(Aggregeringsfunksjon.MAKS, "K9", FeltType.FEILUTBETALT_BELØP.eksternId),
            ),
        )

        val resultat = get<OppgaveQueryService>().query(QueryRequest(query))

        assertThat(resultat).isInstanceOf(OppgaveQueryResultat.GruppertResultat::class.java)
        val rad = (resultat as OppgaveQueryResultat.GruppertResultat).rader.single()
        assertThat(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.SUM }.verdi).isEqualTo("300")
        assertThat(checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.GJENNOMSNITT }.verdi)).startsWith("150")
        assertThat(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.MIN }.verdi).isEqualTo("100")
        assertThat(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.MAKS }.verdi).isEqualTo("200")
    }

    @Test
    fun `queryForGruppering aggregerer timestampfelt datatypebevisst`() {
        val builder = OppgaveTestDataBuilder()
        builder.medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T12:30:00").lagOgLagre()
        builder.medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-14T08:15:00").lagOgLagre()

        val query = OppgaveQuery(
            filtere = listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.EQUALS, Oppgavestatus.AAPEN.kode)
            ),
            select = listOf(
                AggregertSelectFelt(Aggregeringsfunksjon.MIN, "K9", FeltType.MOTTATT_DATO.eksternId),
                AggregertSelectFelt(Aggregeringsfunksjon.MAKS, "K9", FeltType.MOTTATT_DATO.eksternId),
            ),
        )

        val resultat = get<OppgaveQueryService>().query(QueryRequest(query))

        assertThat(resultat).isInstanceOf(OppgaveQueryResultat.GruppertResultat::class.java)
        val rad = (resultat as OppgaveQueryResultat.GruppertResultat).rader.single()
        assertThat(checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.MIN }.verdi)).startsWith("2023-05-14 08:15:00")
        assertThat(checkNotNull(rad.aggregeringer.first { it.type == Aggregeringsfunksjon.MAKS }.verdi)).startsWith("2023-05-15 12:30:00")
    }

    @Test
    fun `queryForGruppering avviser sum på timestampfelt`() {
        val builder = OppgaveTestDataBuilder()
        builder.medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, "2023-05-15T12:30:00").lagOgLagre()

        val query = OppgaveQuery(
            filtere = listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.EQUALS, Oppgavestatus.AAPEN.kode)
            ),
            select = listOf(
                AggregertSelectFelt(Aggregeringsfunksjon.SUM, "K9", FeltType.MOTTATT_DATO.eksternId),
            ),
        )

        val exception = assertThrows<IllegalArgumentException> {
            get<OppgaveQueryService>().query(QueryRequest(query))
        }
        assertThat(exception.message).isNotNull().contains("kun for heltallsfelt")
    }

    private fun queryForOppgave(request: QueryRequest) = get<OppgaveQueryService>().queryForOppgave(request)

    private fun executeQuery(
        request: QueryRequest,
        now: LocalDateTime = LocalDateTime.now()
    ) = get<OppgaveQueryService>().query(request, now)


    private fun lagOppgaveMedPepCache(
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

    private fun lagOppgave(): String {
        val eksternId = UUID.randomUUID().toString()

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


    private fun byggFilter(
        feltType: FeltType,
        operator: EksternFeltverdiOperator,
        vararg verdier: String?
    ): FeltverdiOppgavefilter {
        return FeltverdiOppgavefilter(
            feltType.område,
            feltType.eksternId,
            operator,
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
                "verdi" : "AAPEN"
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
                logger.info(
                    "Oppgave: " + oppgaveRepository.hentNyesteOppgaveForEksternId(
                        tx,
                        "K9",
                        eksternId
                    ).felter.joinToString(", ") { it.eksternId + "-" + it.verdi })
                logger.info(
                    "Pep: " + pepCache.hent("K9", eksternId, tx)
                        ?.run { "kode6-$kode6, kode7-$kode7, egenansatt-$egenAnsatt, oppdater-$oppdatert" })
            }
        }
    }

}
