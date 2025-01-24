package no.nav.k9.los.jobbplanlegger

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TidsvinduTest {
    @Test
    fun `erInnenfor med hele dagen hverdager`() {
        val tidsvindu = Tidsvindu.hverdager()

        val mandagKl12 = LocalDateTime.of(2025, 1, 20, 12, 0)
        val lørdagKl12 = LocalDateTime.of(2025, 1, 25, 12, 0)

        assertThat(tidsvindu.erInnenfor(mandagKl12)).isTrue()
        assertThat(tidsvindu.erInnenfor(lørdagKl12)).isFalse()
    }

    @Test
    fun `erInnenfor med spesifikt tidsrom`() {
        val tidsvindu = Tidsvindu.hverdager(9, 17)

        val mandagKl10 = LocalDateTime.of(2025, 1, 20, 10, 0)
        val mandagKl18 = LocalDateTime.of(2025, 1, 20, 18, 0)

        assertThat(tidsvindu.erInnenfor(mandagKl10)).isTrue()
        assertThat(tidsvindu.erInnenfor(mandagKl18)).isFalse()
    }

    @Test
    fun `motsatt tidsvindu hele dagen`() {
        val tidsvindu = Tidsvindu.hverdager()
        val motsatt = tidsvindu.komplement()

        val mandagKl12 = LocalDateTime.of(2025, 1, 20, 12, 0)
        val lørdagKl12 = LocalDateTime.of(2025, 1, 25, 12, 0)

        assertThat(motsatt.erInnenfor(mandagKl12)).isFalse()
        assertThat(motsatt.erInnenfor(lørdagKl12)).isTrue()
    }

    @Test
    fun `motsatt tidsvindu med spesifikt tidsrom`() {
        val tidsvindu = Tidsvindu.hverdager(9, 17)
        val motsatt = tidsvindu.komplement()

        val mandagKl8 = LocalDateTime.of(2025, 1, 20, 8, 0)
        val mandagKl18 = LocalDateTime.of(2025, 1, 20, 18, 0)
        val lørdagKl12 = LocalDateTime.of(2025, 1, 25, 12, 0)

        assertThat(tidsvindu.erInnenfor(mandagKl8)).isFalse()
        assertThat(tidsvindu.erInnenfor(mandagKl18)).isFalse()
        assertThat(tidsvindu.erInnenfor(lørdagKl12)).isFalse()

        assertThat(motsatt.erInnenfor(mandagKl8)).isTrue()
        assertThat(motsatt.erInnenfor(mandagKl18)).isTrue()
        assertThat(motsatt.erInnenfor(lørdagKl12)).isTrue()
    }
}