package no.nav.k9.los.infrastruktur.abac.tilganger

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.infrastruktur.abac.ISifAbacPdpKlient
import no.nav.k9.los.infrastruktur.abac.SifAbacPdpHttpException
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

internal class SifAbacPdpTilgangsskyggeTest {
    private val token = mockk<IIdToken>()
    private val testTimeout = 2.seconds
    private val alle = Tilganger(basis = true, kode6 = true, oppgavestyring = true, reservering = true, drift = true)
    private val ingen = Tilganger(basis = false, kode6 = false, oppgavestyring = false, reservering = false, drift = false)

    @Test
    fun `logger hvilke tilgangstyper som avviker`() = runTest {
        val skygge = skygge(this) { ingen.copy(drift = true) }

        val logg = fangLogg {
            skygge.observer(token, alle)
        }

        logg shouldBe "Avvik i skyggetilganger fra sif-abac-pdp: tilgangstyper=BASIS,KODE6,OPPGAVESTYRING,RESERVERING"
    }

    @Test
    fun `logger at det ikke er avvik når PDP er enig`() = runTest {
        val skygge = skygge(this) { alle }

        val logg = fangLogg {
            skygge.observer(token, alle)
        }

        logg shouldBe "Ingen avvik i skyggetilganger fra sif-abac-pdp"
    }

    @Test
    fun `gir opp skyggekallet etter timeout når PDP henger`() = runTest {
        val skygge = skygge(this) { awaitCancellation() }

        val logg = fangLogg {
            skygge.observer(token, alle)
        }

        logg shouldBe "Skyggekall mot sif-abac-pdp brukte mer enn $testTimeout"
    }

    @Test
    fun `logger feiltype og statuskode ved HTTP-feil`() = runTest {
        val skygge = skygge(this) { throw SifAbacPdpHttpException(503, "hent-tilganger") }

        val logg = fangLogg {
            skygge.observer(token, alle)
        }

        logg shouldBe "Skyggekall mot sif-abac-pdp feilet: feiltype=SifAbacPdpHttpException, status=503"
    }

    @Test
    fun `logger ikke feilmelding som kan inneholde sensitivt innhold`() = runTest {
        val skygge = skygge(this) { error("sensitiv feiltekst") }

        val logg = fangLogg {
            skygge.observer(token, alle)
        }

        logg shouldBe "Skyggekall mot sif-abac-pdp feilet: feiltype=IllegalStateException, status=ikke_tilgjengelig"
        logg.shouldNotContain("sensitiv feiltekst")
    }

    private fun skygge(scope: CoroutineScope, svar: suspend () -> Tilganger) = SifAbacPdpTilgangsskygge(
        klient = mockk<ISifAbacPdpKlient> { coEvery { hentTilganger(any()) } coAnswers { svar() } },
        skyggeScope = scope,
        // Setter timeouten eksplisitt slik at testen ikke knytter seg til produksjonsdefaulten.
        // Verdien er virtuell tid under runTest, så den koster ingenting i kjøretid.
        timeout = testTimeout,
    )

    /**
     * Skyggen har ingen returverdi å asserte på – logglinja er hele effekten. Kjører
     * advanceUntilIdle() slik at den launch-ede skyggejobben er ferdig før vi leser loggen.
     */
    private fun TestScope.fangLogg(block: () -> Unit): String {
        val logger = LoggerFactory.getLogger(SifAbacPdpTilgangsskygge::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        return try {
            block()
            advanceUntilIdle()
            appender.list.single().formattedMessage
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
