package no.nav.k9.los.uttrekk

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.justRun
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.lagretsok.LagretSøk
import no.nav.k9.los.lagretsok.LagretSøkRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.mapping.CombineOperator
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UttrekkTilgangTest {
    private val repository = mockk<UttrekkRepository>()
    private val søkRepository = mockk<LagretSøkRepository>()
    private val tjeneste = UttrekkTjeneste(repository, søkRepository)

    private fun søk(område: Områder = Områder.K9, eier: Long = 1) = LagretSøk.fraEksisterende(
        id = 10, lagetAv = eier, område = område, versjon = 1,
        tittel = "Test", beskrivelse = "", sistEndret = LocalDateTime.now()
    )

    @Test
    fun `uttrekk krever eier og lagret område uavhengig av kilde`() {
        val kilde = søk()
        every { repository.hent(20) } returns Uttrekk.opprettUttrekk(kilde, 1, false)

        tjeneste.krevTilgang(20, 1, Områder.K9, false)
        assertFailsWith<SecurityException> { tjeneste.krevTilgang(20, 2, Områder.K9, false) }
        assertFailsWith<SecurityException> { tjeneste.krevTilgang(20, 1, Områder.AKTIVITETSPENGER, false) }
        verify { søkRepository wasNot Called }
        verify(exactly = 0) { repository.hentResultat(any()) }
    }

    @Test
    fun `oppretting avviser annen eier feil område og AP før lagring`() {
        every { søkRepository.hent(10) } returns søk()
        val request = OpprettUttrekk(lagretSokId = 10, tittel = "Test", limit = null, offset = null)
        assertFailsWith<SecurityException> { tjeneste.opprett(request, 2, Områder.K9, false) }
        assertFailsWith<SecurityException> { tjeneste.opprett(request, 1, Områder.AKTIVITETSPENGER, false) }
        every { søkRepository.hent(10) } returns søk(Områder.AKTIVITETSPENGER)
        assertFailsWith<IllegalArgumentException> { tjeneste.opprett(request, 1, Områder.AKTIVITETSPENGER, false) }
        verify(exactly = 0) { repository.opprett(any()) }
    }

    @Test
    fun `K9 eier kan fortsatt opprette uttrekk`() {
        every { søkRepository.hent(10) } returns søk()
        every { repository.opprett(any()) } returns 20
        assertEquals(20L, tjeneste.opprett(OpprettUttrekk(10, "Test", null, null), 1, Områder.K9, false))
        verify(exactly = 1) { repository.opprett(any()) }
    }

    @Test
    fun `sletting via kildesøk avviser annen eier før mutasjon`() {
        every { søkRepository.hent(10) } returns søk(eier = 2)
        assertFailsWith<SecurityException> { tjeneste.slettForLagretSøk(10, 1, Områder.K9, false) }
        verify { repository wasNot Called }
    }

    @Test
    fun `bakgrunnsjobb feiler AP kontrollert uten query`() {
        val queryService = mockk<OppgaveQueryService>()
        val kilde = søk(Områder.AKTIVITETSPENGER)
        val uttrekk = Uttrekk.opprettUttrekk(kilde, 1, false)
        every { repository.hent(20) } returns uttrekk
        justRun { repository.oppdater(any(), any()) }
        val jobb = UttrekkJobb(queryService, tjeneste)
        jobb.kjørUttrekk(20)
        assertEquals(UttrekkStatus.FEILET, uttrekk.status)
        verify { queryService wasNot Called }
        verify(exactly = 1) { repository.oppdater(any(), any()) }
    }

    @Test
    fun `servermetadata og jobbsikkerhetsgrense kan ikke overstyres av brukerquery`() {
        for (kode6 in listOf(false, true)) {
            val queryFraBruker = LosObjectMapper.instance.readValue(
                """{"filtere":[], "_uttrekkTilgang":{"område":"AKTIVITETSPENGER","harTilgangTilKode6":${!kode6}},
                    "harTilgangTilKode6":${!kode6}}""", OppgaveQuery::class.java
            )
            val query = queryFraBruker.copy(filtere = listOf(CombineOppgavefilter(CombineOperator.OR, listOf(
                FeltverdiOppgavefilter(null, "personbeskyttelse", EksternFeltverdiOperator.IN, listOf("KODE6", "UGRADERT")),
                FeltverdiOppgavefilter(null, "oppgavetype", EksternFeltverdiOperator.EQUALS, listOf("k9sak")),
            ))))
            val kilde = LagretSøk.fraEksisterende(10, 1, Områder.K9, 1, "Test", "", LocalDateTime.now(), query)
            every { søkRepository.hent(10) } returns kilde
            var lagret: Uttrekk? = null
            every { repository.opprett(any()) } answers { lagret = firstArg(); 20L }
            tjeneste.opprett(OpprettUttrekk(10, "Test", null, null), 1, Områder.K9, kode6)
            val uttrekk = requireNotNull(lagret)
            val json = LosObjectMapper.instance.readTree(uttrekk.lagretQueryJson())
            assertEquals("K9", json.path("_uttrekkTilgang").path("område").asText())
            assertEquals(kode6, json.path("_uttrekkTilgang").path("harTilgangTilKode6").asBoolean())
            every { repository.hent(20) } returns uttrekk
            justRun { repository.oppdater(any(), any()) }
            val queryService = mockk<OppgaveQueryService>()
            every { queryService.query(any(), any()) } returns emptyList()
            UttrekkJobb(queryService, tjeneste).kjørUttrekk(20)
            verify(exactly = 1) {
                queryService.query(match { it.område == Områder.K9 && it.harTilgangTilKode6 == kode6 && it.oppgaveQuery == query }, any())
            }
            assertEquals(UttrekkStatus.FULLFØRT, uttrekk.status)
        }
    }

    @Test
    fun `nedlasting krever eksakt samsvar med lagret kode6 nivå`() {
        for (lagretKode6 in listOf(false, true)) {
            every { repository.hent(20) } returns Uttrekk.opprettUttrekk(søk(), 1, lagretKode6)
            assertFailsWith<SecurityException> { tjeneste.hentResultat(20, 1, Områder.K9, !lagretKode6) }
        }
        verify(exactly = 0) { repository.hentResultat(any()) }
        every { repository.hentResultat(20) } returns "[]"
        assertEquals("[]", tjeneste.hentResultat(20, 1, Områder.K9, true))
    }
}
