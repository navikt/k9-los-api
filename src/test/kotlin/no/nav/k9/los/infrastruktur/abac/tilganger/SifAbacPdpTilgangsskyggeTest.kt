package no.nav.k9.los.infrastruktur.abac.tilganger

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.infrastruktur.abac.ISifAbacPdpKlient
import no.nav.k9.los.infrastruktur.abac.SifAbacPdpHttpException
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

internal class SifAbacPdpTilgangsskyggeTest {
    private val token = mockk<IdToken>()
    private val alle = Tilganger(true, true, true, true, true)
    private val ingen = Tilganger(false, false, false, false, false)

    @Test
    fun `observer venter ikke på PDP`() = runTest {
        val fortsett = CompletableDeferred<Unit>()
        val startet = CompletableDeferred<Unit>()
        val skygge = skygge(this) {
            startet.complete(Unit)
            fortsett.await()
            alle
        }

        skygge.observer(token, alle)
        runCurrent()

        startet.isCompleted shouldBe true
        fortsett.isCompleted shouldBe false
        fortsett.complete(Unit)
    }

    @Test
    fun `logger avvik uten å endre autoritative tilganger`() = runTest {
        val skygge = skygge(this) { ingen }

        val logg = fangLogg {
            skygge.observer(token, alle)
        }

        skygge.finnAvvik(alle, ingen).toList() shouldContainExactly listOf(
            Tilgangstype.BASIS,
            Tilgangstype.KODE6,
            Tilgangstype.OPPGAVESTYRING,
            Tilgangstype.RESERVERING,
            Tilgangstype.DRIFT,
        )
        logg shouldBe "Avvik i skyggetilganger fra sif-abac-pdp: tilgangstyper=BASIS,KODE6,OPPGAVESTYRING,RESERVERING,DRIFT"
    }

    @Test
    fun `logger status ved PDP-feil`() = runTest {
        val skygge = skygge(this) { throw SifAbacPdpHttpException(503, "hent-tilganger") }

        val logg = fangLogg { skygge.observer(token, ingen) }

        logg shouldBe "Skyggekall mot sif-abac-pdp feilet: feiltype=SifAbacPdpHttpException, status=503"
    }

    @Test
    fun `logger ikke sensitiv feilmelding når klienten kaster`() = runTest {
        val skygge = skygge(this) { error("sensitiv feiltekst skal ikke logges") }

        val logg = fangLogg { skygge.observer(token, alle) }

        logg shouldBe "Skyggekall mot sif-abac-pdp feilet: feiltype=IllegalStateException, status=ikke_tilgjengelig"
        logg.shouldNotContain("sensitiv feiltekst")
    }

    @Test
    fun `sammenligner ikke drift når PDP-kontrakten mangler feltet`() {
        val skygge = skygge(TestScope()) { ingen }
        val kunDrift = ingen.copy(drift = true)

        skygge.finnAvvik(kunDrift, ingen) shouldBe setOf(Tilgangstype.DRIFT)
    }

    private fun skygge(scope: TestScope, svar: suspend () -> Tilganger) = SifAbacPdpTilgangsskygge(
        klient = mockk<ISifAbacPdpKlient> {
            coEvery { hentTilganger(any()) } coAnswers { svar() }
        },
        skyggeScope = scope,
    )

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
