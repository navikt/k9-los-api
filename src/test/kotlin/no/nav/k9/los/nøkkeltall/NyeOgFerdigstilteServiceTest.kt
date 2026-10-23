package no.nav.k9.los.nøkkeltall

import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.*
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class NyeOgFerdigstilteServiceTest {
    @Test
    fun `K9 beholder grupper og respons fra eksisterende service`() {
        val kilde = mockk<K9NyeOgFerdigstilteService>()
        val adapter = K9NyeOgFerdigstilteAdapter(kilde)
        val response = NyeOgFerdigstilteResponse(null)
        every { kilde.hentCachetVerdi(any()) } returns response

        assertEquals(NyeOgFerdigstilteGruppe.entries.map { it.name }, adapter.grupper.map { it.kode })
        for (gruppe in NyeOgFerdigstilteGruppe.entries) {
            assertSame(response, adapter.hentCachetVerdi(gruppe.name))
            verify(exactly = 1) { kilde.hentCachetVerdi(gruppe) }
        }
    }

    @Test
    fun `dispatcher velger riktig service for henting og oppdatering`() {
        val k9 = mockk<K9NyeOgFerdigstilteAdapter>(relaxed = true)
        val akt = mockk<AktivitetspengerNyeOgFerdigstilteService>(relaxed = true)
        val service = NyeOgFerdigstilteService(k9, akt)
        val k9Svar = NyeOgFerdigstilteResponse(null)
        val aktSvar = NyeOgFerdigstilteResponse(null, grupper = listOf(KodeOgNavn("ALLE", "Alle behandlinger")))
        every { k9.hentCachetVerdi("ALLE") } returns k9Svar
        every { akt.hentCachetVerdi("ALLE") } returns aktSvar

        assertSame(k9Svar, service.hentCachetVerdi(Områder.K9, "ALLE"))
        assertSame(aktSvar, service.hentCachetVerdi(Områder.AKTIVITETSPENGER, "ALLE"))
        runBlocking {
            service.oppdaterCache(Områder.K9, this)
            service.oppdaterCache(Områder.AKTIVITETSPENGER, this)
        }
        verify(exactly = 1) { k9.oppdaterCache(any()) }
        verify(exactly = 1) { akt.oppdaterCache(any()) }
    }

    @Test
    fun `aktivitetspenger returnerer egne grupper og sju dager også før cache er fylt`() {
        val queryService = mockk<OppgaveQueryService>()
        val service = AktivitetspengerNyeOgFerdigstilteService(queryService)
        val svar = service.hentCachetVerdi("ALLE")
        val iDag = LocalDate.now()
        val forventedeDatoer = (6L downTo 0L).map { iDag.minusDays(it).format(DateTimeFormatter.ofPattern("dd.MM")) }
        assertNull(svar.oppdatertTidspunkt)
        assertEquals(listOf(
            KodeOgNavn("ALLE", "Alle behandlinger"),
            KodeOgNavn("NAVKONTOR", "Nav-kontor"),
            KodeOgNavn("NAVFORVALTNING", "Nav-forvaltning"),
            KodeOgNavn("KLAGE", "Klager"),
            KodeOgNavn("FEILUTBETALING", "Feilutbetalinger"),
        ), svar.grupper)
        assertEquals(forventedeDatoer, svar.kolonner)
        assertEquals(emptyList(), svar.serier)
        assertFailsWith<IllegalArgumentException> { service.hentCachetVerdi("PUNSJ") }

        every { queryService.query(any(), any()) } returns emptyList()
        runBlocking { service.oppdaterCache(this) }
        val cachet = service.hentCachetVerdi("ALLE")
        assertEquals(listOf("Nye", "Ferdigstilte"), cachet.serier.map { it.navn })
        assertEquals(listOf(List(7) { 0 }, List(7) { 0 }), cachet.serier.map { it.data })

        clearMocks(queryService, answers = false)
        assertSame(cachet, service.hentCachetVerdi("ALLE"))
        verify { queryService wasNot Called }
    }
}
