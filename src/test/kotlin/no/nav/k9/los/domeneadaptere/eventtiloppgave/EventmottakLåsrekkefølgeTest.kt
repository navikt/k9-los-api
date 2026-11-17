package no.nav.k9.los.domeneadaptere.eventtiloppgave

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.AbstractPostgresTest
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.infrastruktur.db.Appversjonsvakt
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventmottakLåsrekkefølgeTest : AbstractPostgresTest() {

    private val gammel = Appversjonsvakt(dataSource, versjon = "gammel")
    private val ny = Appversjonsvakt(dataSource, versjon = "ny")
    private val eventRepository = EventRepository(dataSource, gammel)
    private val transactionalManager = TransactionalManager(dataSource)
    private val eksternId = "ekstern-1"


    @Test
    fun `mottak venter bak overtakelse uten å holde eventnøkkel som historikkvask venter på`() {
        gammel.taOver()
        transactionalManager.transaction { eventRepository.lagre(Fagsystem.K9SAK, eksternId, "2024-01-01T00:00", "{}", it) }

        val versjonslåsTatt = CountDownLatch(1)
        val låsEventnøkkel = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)
        try {
            val historikkvask = executor.submit {
                transactionalManager.transaction { tx ->
                    check(gammel.erAktiv(tx))
                    versjonslåsTatt.countDown()
                    låsEventnøkkel.await(10, TimeUnit.SECONDS)
                    eventRepository.hentOgLåsEventnøkkel(Fagsystem.K9SAK, eksternId, tx)
                }
            }
            assertTrue(versjonslåsTatt.await(10, TimeUnit.SECONDS))

            val overtakelse = executor.submit { ny.taOver() }
            Thread.sleep(300)
            assertFalse(overtakelse.isDone, "Overtakelse skal vente på historikkvasken")

            val mottak = executor.submit<Boolean> {
                transactionalManager.transaction { tx ->
                    eventRepository.lagreOgSjekkAktiv(Fagsystem.K9SAK, eksternId, "2024-01-02T00:00", "{}", tx) != null
                }
            }
            Thread.sleep(300)
            assertFalse(mottak.isDone, "Mottak skal vente bak overtakelsen")

            låsEventnøkkel.countDown()
            historikkvask.get(10, TimeUnit.SECONDS)
            overtakelse.get(10, TimeUnit.SECONDS)
            assertFalse(mottak.get(10, TimeUnit.SECONDS), "Gammel versjon skal ikke prosessere eventet")

            assertEquals(true, dirty("2024-01-02T00:00"), "Eventet skal lagres som dirty for aktiv versjon")
        } finally {
            låsEventnøkkel.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `mottak i inaktiv versjon lagrer eventet som dirty uten å returnere nøkkel`() {
        ny.taOver()

        val nøkkel = transactionalManager.transaction {
            eventRepository.lagreOgSjekkAktiv(Fagsystem.K9SAK, eksternId, "2024-01-01T00:00", "{}", it)
        }

        assertNull(nøkkel)
        assertEquals(true, dirty("2024-01-01T00:00"))
    }

    @Test
    fun `mottak i aktiv versjon returnerer nøkkelen til lagret event`() {
        gammel.taOver()

        val nøkkel = transactionalManager.transaction {
            eventRepository.lagreOgSjekkAktiv(Fagsystem.K9SAK, eksternId, "2024-01-01T00:00", "{}", it)
        }

        assertEquals(Fagsystem.K9SAK, nøkkel?.fagsystem)
        assertEquals(eksternId, nøkkel?.eksternId)
        assertEquals(true, dirty("2024-01-01T00:00"))
    }

    private fun dirty(eksternVersjon: String): Boolean? =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                        select e.dirty from event e join event_nokkel en on en.id = e.event_nokkel_id
                        where en.fagsystem = :fagsystem and en.ekstern_id = :eksternId and e.ekstern_versjon = :eksternVersjon
                    """.trimIndent(),
                    mapOf("fagsystem" to Fagsystem.K9SAK.kode, "eksternId" to eksternId, "eksternVersjon" to eksternVersjon)
                ).map { it.boolean("dirty") }.asSingle
            )
        }
}
