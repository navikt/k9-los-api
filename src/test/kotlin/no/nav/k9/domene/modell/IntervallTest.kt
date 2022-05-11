package no.nav.k9.domene.modell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

internal class IntervallTest {

    @Test
    fun erUtenforTest() {
        assertTrue(Intervall(1, 10).erUtenfor(0))
        assertFalse(Intervall(1, 10).erUtenfor(5))

        assertFalse(Intervall(1.1, 10.6).erUtenfor(10.5))

        assertFalse(Intervall(1, 10).erUtenfor(1))
        assertFalse(Intervall(1, 10).erUtenfor(10))

        assertTrue(Intervall(null, 10).erUtenfor(11))
        assertFalse(Intervall(null, 10).erUtenfor(10))
        assertFalse(Intervall(null, 10).erUtenfor(-100))

        assertTrue(Intervall(1, null).erUtenfor(0))
        assertTrue(Intervall(1, null).erUtenfor(-12))
        assertFalse(Intervall(1, null).erUtenfor(10))

        assertFalse(Intervall(50, 50).erUtenfor(50))
        assertTrue(Intervall(50, 50).erUtenfor(10))


    }

    @Test
    fun `skal feile hvis fom og tom er null`() {
        assertFailsWith(IllegalArgumentException::class) {
            Intervall<Int>(null, null)
        }
    }
}