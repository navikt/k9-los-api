package no.nav.k9.los.nøkkeltall

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.nøkkeltall.avdelingsleder.dagenstall.*
import no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte.*
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InngangOgFerdigstilteServiceTest {
    @Test
    fun `K9-adapter beholder hele legacy-responsen med nye DTO-navn`() {
        val linje = DagensTallLinjeDto("Inngang", 7)
        val inngang = DagensTallKortDto(linje, emptyList())
        val ferdigstilte = DagensTallKortDto(
            DagensTallLinjeDto("Ferdigstilt", 5),
            listOf(DagensTallLinjeDto("manuelt", 3), DagensTallLinjeDto("automatisk", 2)),
        )
        val legacy = DagensTallResponse(
            LocalDateTime.now(),
            DagensTallHovedgruppe.entries.map { KodeOgNavn(it.name, it.navn) },
            DagensTallUndergruppe.entries.map { KodeOgNavn(it.name, it.navn) },
            listOf(DagensTallDto(
                DagensTallHovedgruppe.PLEIEPENGER_SYKT_BARN,
                DagensTallUndergruppe.FØRSTEGANG,
                mapOf("idag" to (inngang to ferdigstilte)),
                mapOf("2026-10" to (inngang to ferdigstilte)),
            )),
        )
        val kilde = mockk<K9DagensTallService>()
        every { kilde.hentCachetVerdi() } returns legacy
        val adapter = K9InngangOgFerdigstilteService(kilde)
        val mapper = ObjectMapper().findAndRegisterModules()

        assertEquals(mapper.valueToTree<JsonNode>(legacy), mapper.valueToTree<JsonNode>(adapter.hentCachetVerdi()))
        verify(exactly = 1) { kilde.hentCachetVerdi() }
    }

    @Test
    fun `dispatcher velger riktig område uten å dele cache`() {
        val k9 = mockk<K9InngangOgFerdigstilteService>()
        val aktivitetspenger = mockk<AktivitetspengerInngangOgFerdigstilteService>()
        val service = InngangOgFerdigstilteService(k9, aktivitetspenger)
        val k9Verdi = InngangOgFerdigstilteResponse(null, emptyList(), emptyList(), emptyList())
        val aktVerdi = InngangOgFerdigstilteResponse(LocalDateTime.now(), emptyList(), emptyList(), emptyList())
        every { k9.hentCachetVerdi() } returns k9Verdi
        every { aktivitetspenger.hentCachetVerdi() } returns aktVerdi
        every { k9.oppdaterCache(any()) } returns Unit
        every { aktivitetspenger.oppdaterCache(any()) } returns Unit

        assertSame(k9Verdi, service.hentCachetVerdi(Områder.K9))
        assertSame(aktVerdi, service.hentCachetVerdi(Områder.AKTIVITETSPENGER))
        runBlocking {
            service.oppdaterCache(Områder.K9, this)
            service.oppdaterCache(Områder.AKTIVITETSPENGER, this)
        }
        verify(exactly = 1) { k9.oppdaterCache(any()) }
        verify(exactly = 1) { aktivitetspenger.oppdaterCache(any()) }
    }

    @Test
    fun `aktivitetspenger har tom respons før oppdatering og cacher alle grupper etter oppdatering`() {
        val queryService = mockk<OppgaveQueryService>()
        every { queryService.query(any(), any()) } returns emptyList()
        val service = AktivitetspengerInngangOgFerdigstilteService(queryService)

        assertNull(service.hentCachetVerdi().oppdatertTidspunkt)
        assertTrue(service.hentCachetVerdi().tall.isEmpty())
        runBlocking { service.oppdaterCache(this) }
        val verdi = service.hentCachetVerdi()

        assertNotNull(verdi.oppdatertTidspunkt)
        assertEquals(listOf("ALLE", "DEL1", "DEL2"), verdi.hovedgrupper.map { it.kode })
        assertEquals(listOf("TOTALT", "FØRSTEGANG", "REVURDERING", "KLAGE", "FEILUTBETALING"), verdi.undergrupper.map { it.kode })
        assertEquals(15, verdi.tall.size)
        assertTrue(verdi.tall.all { it.serier.keys == setOf("idag", "sisteUke", "siste2Uker", "siste4Uker") })
        assertTrue(verdi.tall.all { rad -> rad.serier.values.all { it.first.hovedtall.verdi == 0L && it.second.hovedtall.verdi == 0L } })
        assertSame(verdi, service.hentCachetVerdi())
        verify(exactly = 8) { queryService.query(any(), any()) }
    }
}
