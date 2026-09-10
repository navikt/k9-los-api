package no.nav.k9.los.infrastruktur.abac.cache

import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PepCacheOmrådeTest {
    private val pep = mockk<IPepClient>()
    private val repository = mockk<PepCacheRepository>(relaxed = true)
    private val tx = mockk<TransactionalSession>()
    private val service = PepCacheService(pep, repository, mockk())

    @Test
    fun `sak og person klassifiseres og lagres i faktisk område`() = runBlocking {
        for (område in Områder.entries) {
            coEvery { pep.diskresjonskoderForSak("sak", område) } returns setOf(Diskresjonskode.KODE6)
            coEvery { pep.diskresjonskoderForPerson("person", område) } returns setOf(Diskresjonskode.KODE7)
            val sak = service.oppdater(tx, PepCacheInput("samme-id", "sak", emptyList(), område, "testtype"))
            val person = service.oppdater(tx, PepCacheInput("person-id", null, listOf("person"), område, "testtype"))

            assertEquals(område, sak.område)
            assertEquals(område, sak.kildeområde)
            assertTrue(sak.kode6)
            assertEquals(område, person.område)
            assertTrue(person.kode7)
            verify { repository.lagre(sak, tx); repository.lagre(person, tx) }
            coVerify(exactly = 1) { pep.diskresjonskoderForSak("sak", område) }
            coVerify(exactly = 1) { pep.diskresjonskoderForPerson("person", område) }
        }
    }

    @Test
    fun `manglende klassifiseringsgrunnlag lagres ikke som ugradert`() {
        for (område in Områder.entries) {
            for (oppgavetype in listOf("k9punsj", "k9sak", "ukjent", "")) {
                if (område == Områder.K9 && oppgavetype == "k9punsj") continue
                assertThrows<IllegalArgumentException> {
                    runBlocking { service.oppdater(tx, PepCacheInput("ukjent", null, emptyList(), område, oppgavetype)) }
                }
            }
        }
        verify(exactly = 0) { repository.lagre(any(), any()) }
        coVerify(exactly = 0) { pep.diskresjonskoderForSak(any(), any()); pep.diskresjonskoderForPerson(any(), any()) }
    }

    @Test
    fun `kun aktørløs K9-punsj kan lagres ugradert uten PDP`() = runBlocking {
        val cache = service.oppdater(tx, PepCacheInput("punsj", null, emptyList(), Områder.K9, "k9punsj"))

        assertEquals(Områder.K9, cache.område)
        assertEquals(false, cache.kode6)
        assertEquals(false, cache.kode7)
        assertEquals(false, cache.egenAnsatt)
        verify(exactly = 1) { repository.lagre(cache, tx) }
        coVerify(exactly = 0) { pep.diskresjonskoderForSak(any(), any()); pep.diskresjonskoderForPerson(any(), any()) }
    }

    @Test
    fun `K9-punsj med sak eller aktør må fortsatt klassifiseres av PDP`() = runBlocking {
        coEvery { pep.diskresjonskoderForSak("sak", Områder.K9) } returns setOf(Diskresjonskode.KODE6)
        coEvery { pep.diskresjonskoderForPerson("person", Områder.K9) } returns setOf(Diskresjonskode.KODE6)

        assertTrue(service.oppdater(tx, PepCacheInput("punsj-sak", "sak", emptyList(), Områder.K9, "k9punsj")).kode6)
        assertTrue(service.oppdater(tx, PepCacheInput("punsj-person", null, listOf("person"), Områder.K9, "k9punsj")).kode6)
        coVerify(exactly = 1) { pep.diskresjonskoderForSak("sak", Områder.K9) }
        coVerify(exactly = 1) { pep.diskresjonskoderForPerson("person", Områder.K9) }
    }

    @Test
    fun `PDP-feil lagres ikke som ugradert`() {
        coEvery { pep.diskresjonskoderForSak(any(), any()) } throws IllegalStateException("Utilgjengelig")
        assertThrows<IllegalStateException> {
            runBlocking { service.oppdater(tx, PepCacheInput("ukjent", "sak", emptyList(), Områder.AKTIVITETSPENGER, "testtype")) }
        }
        verify(exactly = 0) { repository.lagre(any(), any()) }
    }
}
