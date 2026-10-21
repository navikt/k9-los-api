package no.nav.k9.los.nøkkeltall

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.kodeverk.PersonBeskyttelseType
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.AktivitetspengerStatusFordelingService
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.K9StatusFordelingService
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.query.dto.query.Aggregeringsfunksjon
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.dto.resultat.Aggregertverdi
import no.nav.k9.los.oppgaveuthenting.query.dto.resultat.OppgaveQueryRad
import no.nav.k9.los.oppgaveuthenting.query.dto.resultat.Oppgavefeltverdi
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StatusFordelingServiceTest {
    private val queryService = mockk<OppgaveQueryService>()
    private val k9 = K9StatusFordelingService(queryService)
    private val aktivitetspenger = AktivitetspengerStatusFordelingService(queryService)
    private val service = StatusFordelingService(k9, aktivitetspenger)
    private val del1 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode
    private val del2 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode

    @Test
    fun `aktivitetspenger teller samlet og per del med tilsvarende kildespørringer`() {
        val forespørsler = mutableListOf<QueryRequest>()
        every { queryService.query(capture(forespørsler), any()) } answers {
            when (firstArg<QueryRequest>().oppgaveQuery.filter("oppgavetype").verdi) {
                listOf(del1) -> listOf(rad(Oppgavestatus.AAPEN, 2), rad(Oppgavestatus.VENTER, 3))
                listOf(del2) -> listOf(rad(Oppgavestatus.AAPEN, 7), rad(Oppgavestatus.UAVKLART, 5))
                else -> listOf(rad(Oppgavestatus.AAPEN, 9), rad(Oppgavestatus.VENTER, 3), rad(Oppgavestatus.UAVKLART, 5))
            }
        }

        val kort = service.hentVerdi(Områder.AKTIVITETSPENGER, kode6 = false).tall

        assertEquals(listOf("BEHANDLINGER", "DEL1", "DEL2"), kort.map { it.tittel.kode })
        assertEquals(listOf(17L, 5L, 12L), kort.map { it.bunnlinje.verdi })
        assertEquals(listOf(9L, 2L, 7L), kort.map { it.topplinje.verdi })
        assertEquals(listOf("venter"), kort[1].linjer.map { it.visningsnavn })
        assertEquals(listOf("uavklart"), kort[2].linjer.map { it.visningsnavn })
        assertEquals(3, forespørsler.size)
        assertTrue(forespørsler.all { it.område == Områder.AKTIVITETSPENGER })

        kort.forEachIndexed { indeks, statuskort ->
            val forespørsel = forespørsler[indeks].oppgaveQuery
            assertEquals(forespørsel.filtere, statuskort.bunnlinje.kildespørring.filtere)
            (listOf(statuskort.topplinje, statuskort.bunnlinje) + statuskort.linjer).forEach { linje ->
                assertEquals(forespørsel.filter("oppgavetype"), linje.kildespørring.filter("oppgavetype"))
                assertEquals(listOf(PersonBeskyttelseType.UTEN_KODE6.kode), linje.kildespørring.filter("personbeskyttelse").verdi)
                assertTrue(linje.kildespørring.select.isEmpty())
            }
            assertEquals(EksternFeltverdiOperator.EQUALS, statuskort.topplinje.kildespørring.filter("oppgavestatus").operator)
            assertEquals(listOf(Oppgavestatus.AAPEN.kode), statuskort.topplinje.kildespørring.filter("oppgavestatus").verdi)
        }
    }

    @Test
    fun `tomme kort utelates mens kort med bare ventende oppgaver beholder åpne lik null`() {
        every { queryService.query(any(), any()) } answers {
            if (firstArg<QueryRequest>().oppgaveQuery.filter("oppgavetype").verdi == listOf(del2)) emptyList()
            else listOf(rad(Oppgavestatus.VENTER, 4))
        }

        val kort = aktivitetspenger.hentVerdi(kode6 = false).tall

        assertEquals(listOf("BEHANDLINGER", "DEL1"), kort.map { it.tittel.kode })
        assertTrue(kort.all { it.topplinje.verdi == 0L && it.bunnlinje.verdi == 4L })
    }

    @Test
    fun `cache er adskilt mellom områder og kode6 og kan oppdateres`() {
        val forespørsler = mutableListOf<QueryRequest>()
        every { queryService.query(capture(forespørsler), any()) } returns emptyList()
        every { queryService.queryForAntall(any(), any()) } returns 0L

        val utenKode6 = service.hentVerdi(Områder.AKTIVITETSPENGER, false)
        assertSame(utenKode6, service.hentVerdi(Områder.AKTIVITETSPENGER, false))
        service.hentVerdi(Områder.AKTIVITETSPENGER, true)
        service.hentVerdi(Områder.K9, false)

        assertEquals(3, forespørsler.count { it.område == Områder.AKTIVITETSPENGER && it.oppgaveQuery.filter("personbeskyttelse").verdi == listOf(PersonBeskyttelseType.KODE6.kode) })
        assertEquals(6, forespørsler.count { it.område == Områder.K9 })

        service.oppdaterCache(Områder.AKTIVITETSPENGER, false)
        val oppdatert = service.hentVerdi(Områder.AKTIVITETSPENGER, false)
        assertTrue(oppdatert !== utenKode6)
        assertSame(oppdatert, service.hentVerdi(Områder.AKTIVITETSPENGER, false))
        verify(exactly = 15) { queryService.query(any(), any()) }
        verify(exactly = 1) { queryService.queryForAntall(any(), any()) }
    }

    @Test
    fun `K9 beholder sine seks statuskort og egen klagefordeling`() {
        every { queryService.query(any(), any()) } returns listOf(
            rad(Oppgavestatus.AAPEN, 2), rad(Oppgavestatus.VENTER, 3), rad(Oppgavestatus.UAVKLART, 1),
        )
        every { queryService.queryForAntall(any(), any()) } returns 2L

        val kort = service.hentVerdi(Områder.K9, false).tall

        assertEquals(listOf("BEHANDLINGER", "FØRSTEGANG", "REVURDERING", "FEILUTBETALING", "KLAGE", "PUNSJ"), kort.map { it.tittel.kode })
        assertTrue(kort.all { it.bunnlinje.verdi == 6L })
        assertEquals(listOf("venter Kabal", "venter annet", "uavklart"), kort[4].linjer.map { it.visningsnavn })
        assertEquals(listOf(2L, 1L, 1L), kort[4].linjer.map { it.verdi })
    }

    private fun OppgaveQuery.filter(kode: String) = filtere.filterIsInstance<FeltverdiOppgavefilter>().single { it.kode == kode }

    private fun rad(status: Oppgavestatus, antall: Long) = OppgaveQueryRad(
        feltverdier = listOf(Oppgavefeltverdi(null, "oppgavestatus", status.kode)),
        aggregeringer = listOf(Aggregertverdi(Aggregeringsfunksjon.ANTALL, null, null, antall)),
    )
}
