package no.nav.k9.los.domene.modell

import no.nav.k9.los.domene.modell.Intervall
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class FasteIntervallerTest {

    @Test
    fun erUtenforTest() {
        Assertions.assertTrue(Intervall(1, 10).erUtenfor(0))
        Assertions.assertFalse(Intervall(1, 10).erUtenfor(5))

        Assertions.assertFalse(Intervall(1.1, 10.6).erUtenfor(10.5))

        Assertions.assertFalse(Intervall(1, 10).erUtenfor(1))
        Assertions.assertFalse(Intervall(1, 10).erUtenfor(10))

        Assertions.assertTrue(Intervall(null, 10).erUtenfor(11))
        Assertions.assertFalse(Intervall(null, 10).erUtenfor(10))
        Assertions.assertFalse(Intervall(null, 10).erUtenfor(-100))

        Assertions.assertTrue(Intervall(1, null).erUtenfor(0))
        Assertions.assertTrue(Intervall(1, null).erUtenfor(-12))
        Assertions.assertFalse(Intervall(1, null).erUtenfor(10))

        Assertions.assertFalse(Intervall(50, 50).erUtenfor(50))
        Assertions.assertTrue(Intervall(50, 50).erUtenfor(10))


    }

    @Test
    fun `skal feile hvis fom og tom er null`() {
        assertFailsWith(IllegalArgumentException::class) {
            Intervall<Int>(null, null)
        }
    }
}