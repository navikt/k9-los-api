package no.nav.k9.los.infrastruktur.abac.tilganger

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

internal class SifAbacPdpTilgangsskyggeTest {
    private val token = mockk<IIdToken>()
    private val alle = Tilganger(true, true, true, true, true)
    private val ingen = Tilganger(false, false, false, false, false)

    @Test
    fun `returnerer alltid dagens tilganger når PDP er enig`() = runTest {
        val skygge = SifAbacPdpTilgangsskygge { PdpResultat.Suksess(alle) }

        skygge.observerOgReturnerAutoritative(token) { alle } shouldBe alle
    }

    @Test
    fun `returnerer alltid dagens tilganger når PDP er uenig i alle felt`() = runTest {
        val skygge = SifAbacPdpTilgangsskygge { PdpResultat.Suksess(ingen) }

        val logg = fangLogg {
            skygge.observerOgReturnerAutoritative(token) { alle } shouldBe alle
        }
        skygge.finnAvvik(alle, PdpResultat.Suksess(ingen)).toList() shouldContainExactly listOf(
            Tilgangstype.BASIS,
            Tilgangstype.KODE6,
            Tilgangstype.OPPGAVESTYRING,
            Tilgangstype.RESERVERING,
            Tilgangstype.DRIFT,
        )
        logg shouldBe "Avvik i skyggetilganger fra sif-abac-pdp: tilgangstyper=BASIS,KODE6,OPPGAVESTYRING,RESERVERING,DRIFT"
    }

    @Test
    fun `returnerer alltid dagens tilganger ved PDP-feil`() = runTest {
        val skygge = SifAbacPdpTilgangsskygge { PdpResultat.Feil("HTTP", 503) }

        val logg = fangLogg {
            skygge.observerOgReturnerAutoritative(token) { ingen } shouldBe ingen
        }
        logg shouldBe "Skyggekall mot sif-abac-pdp feilet: feiltype=HTTP, status=503"
    }

    @Test
    fun `returnerer alltid dagens tilganger når PDP-klienten kaster`() = runTest {
        val skygge = SifAbacPdpTilgangsskygge { error("sensitiv feiltekst skal ikke logges") }

        val logg = fangLogg {
            skygge.observerOgReturnerAutoritative(token) { alle } shouldBe alle
        }
        logg shouldBe "Skyggekall mot sif-abac-pdp feilet: feiltype=IllegalStateException, status=ikke_tilgjengelig"
        logg.shouldNotContain("sensitiv feiltekst")
    }

    @Test
    fun `PDP kan aldri gi tilgang som dagens modell nekter`() = runTest {
        val skygge = SifAbacPdpTilgangsskygge { PdpResultat.Suksess(alle) }

        skygge.observerOgReturnerAutoritative(token) { ingen } shouldBe ingen
    }

    @Test
    fun `starter PDP og dagens beregning parallelt og venter på begge`() = runTest {
        val pdpStartet = AtomicBoolean(false)
        val autoritativStartet = AtomicBoolean(false)
        val skygge = SifAbacPdpTilgangsskygge {
            pdpStartet.set(true)
            while (!autoritativStartet.get()) delay(1)
            PdpResultat.Suksess(alle)
        }

        val resultat = skygge.observerOgReturnerAutoritative(token) {
            autoritativStartet.set(true)
            while (!pdpStartet.get()) delay(1)
            alle
        }

        resultat shouldBe alle
        pdpStartet.get() shouldBe true
        autoritativStartet.get() shouldBe true
    }

    @Test
    fun `rapporterer avvik i drift`() {
        val skygge = SifAbacPdpTilgangsskygge { PdpResultat.Suksess(ingen) }
        val kunDrift = ingen.copy(drift = true)

        skygge.finnAvvik(kunDrift, PdpResultat.Suksess(ingen)) shouldBe setOf(Tilgangstype.DRIFT)
    }

    private suspend fun fangLogg(block: suspend () -> Unit): String {
        val logger = LoggerFactory.getLogger(SifAbacPdpTilgangsskygge::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        return try {
            block()
            appender.list.single().formattedMessage
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
