package no.nav.k9.los.infrastruktur.db

import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.AbstractPostgresTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppversjonsvaktTest : AbstractPostgresTest() {

    private val gammel = Appversjonsvakt(dataSource, versjon = "gammel")
    private val ny = Appversjonsvakt(dataSource, versjon = "ny")

    private fun erAktiv(vakt: Appversjonsvakt): Boolean =
        using(sessionOf(dataSource)) { session -> session.transaction { vakt.erAktiv(it) } }

    @Test
    fun `alle versjoner er aktive når ingen versjon er registrert`() {
        assertTrue(erAktiv(gammel))
        assertTrue(erAktiv(ny))
    }

    @Test
    fun `etter overtakelse er bare ny versjon aktiv`() {
        gammel.taOver()
        assertTrue(erAktiv(gammel))
        assertFalse(erAktiv(ny))

        ny.taOver()
        assertFalse(erAktiv(gammel))
        assertTrue(erAktiv(ny))
    }

    @Test
    fun `taOverHvisTabellFinnes tar over når tabellen finnes`() {
        assertTrue(ny.taOverHvisTabellFinnes())
        assertFalse(erAktiv(gammel))
    }

    @Test
    fun `overtakelse venter til pågående transaksjon med delt lås er ferdig`() {
        gammel.taOver()
        val låsTatt = CountDownLatch(1)
        val slippLås = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val gammelTransaksjon = executor.submit<Boolean> {
                using(sessionOf(dataSource)) { session ->
                    session.transaction { tx ->
                        val aktiv = gammel.erAktiv(tx)
                        låsTatt.countDown()
                        slippLås.await(10, TimeUnit.SECONDS)
                        aktiv
                    }
                }
            }
            assertTrue(låsTatt.await(10, TimeUnit.SECONDS))

            val overtakelse = executor.submit { ny.taOver() }
            Thread.sleep(500)
            assertFalse(overtakelse.isDone, "Overtakelse skal vente på delt lås")

            slippLås.countDown()
            assertTrue(gammelTransaksjon.get(10, TimeUnit.SECONDS))
            overtakelse.get(10, TimeUnit.SECONDS)

            assertFalse(erAktiv(gammel))
            assertTrue(erAktiv(ny))
        } finally {
            slippLås.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `gammel versjon som venter bak overtakelse ser ny versjon`() {
        gammel.taOver()
        val låsTatt = CountDownLatch(1)
        val slippLås = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)
        try {
            executor.submit {
                using(sessionOf(dataSource)) { session ->
                    session.transaction { tx ->
                        gammel.erAktiv(tx)
                        låsTatt.countDown()
                        slippLås.await(10, TimeUnit.SECONDS)
                    }
                }
            }
            assertTrue(låsTatt.await(10, TimeUnit.SECONDS))
            val overtakelse = executor.submit { ny.taOver() }
            Thread.sleep(300)
            val nyGammelTransaksjon = executor.submit<Boolean> { erAktiv(gammel) }
            Thread.sleep(300)

            slippLås.countDown()
            overtakelse.get(10, TimeUnit.SECONDS)
            assertEquals(false, nyGammelTransaksjon.get(10, TimeUnit.SECONDS))
        } finally {
            slippLås.countDown()
            executor.shutdownNow()
        }
    }
}
