package no.nav.k9.los.infrastruktur.abac

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import org.junit.jupiter.api.Test
import java.util.UUID

class PepClientAktivitetspengerTest {
    private val graph = mockk<IAzureGraphService>()
    private val pdp = mockk<SifAbacPdpKlient>()
    private val pep = PepClient(graph, pdp)
    private val område = Områder.AKTIVITETSPENGER
    private val saksbehandler = mockk<Saksbehandler> { every { navident } returns "Z123456" }
    private val grupper = setOf(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    @Test
    fun `sjekker mottakers ident og grupper og returnerer PDP-beslutningen`() = runTest {
        val oppgave = oppgave("123456789")
        coEvery { graph.hentGrupperForSaksbehandler("Z123456") } returns grupper
        for (tilgang in listOf(true, false)) {
            coEvery { pdp.harTilgangTilSak(område, Action.reserver, SaksnummerDto("123456789"), "Z123456", grupper) } returns tilgang

            pep.harTilgangTilOppgaveV3(område, oppgave, saksbehandler, Action.reserver) shouldBe tilgang
        }
        coVerify(exactly = 2) { pdp.harTilgangTilSak(område, Action.reserver, SaksnummerDto("123456789"), "Z123456", grupper) }
    }

    @Test
    fun `manglende saksnummer gir avslag uten eksterne kall`() = runTest {
        for (saksnummer in listOf(null, "", " ")) {
            pep.harTilgangTilOppgaveV3(område, oppgave(saksnummer), saksbehandler, Action.reserver) shouldBe false
        }
        coVerify { listOf(graph, pdp) wasNot Called }
    }

    @Test
    fun `feil fra Graph gir ikke tilgang eller PDP-kall`() = runTest {
        coEvery { graph.hentGrupperForSaksbehandler("Z123456") } throws IllegalStateException("Graph utilgjengelig")
        shouldThrow<IllegalStateException> {
            pep.harTilgangTilOppgaveV3(område, oppgave("123456789"), saksbehandler, Action.reserver)
        }
        coVerify { pdp wasNot Called }
    }

    @Test
    fun `oppgave fra annet område avvises`() = runTest {
        shouldThrow<IllegalArgumentException> {
            pep.harTilgangTilOppgaveV3(Områder.K9, oppgave("123456789"), saksbehandler, Action.reserver)
        }
        coVerify { listOf(graph, pdp) wasNot Called }
    }

    private fun oppgave(saksnummer: String?) = mockk<Oppgave> {
        every { område } returns Områder.AKTIVITETSPENGER
        every { hentVerdi("saksnummer") } returns saksnummer
    }
}
