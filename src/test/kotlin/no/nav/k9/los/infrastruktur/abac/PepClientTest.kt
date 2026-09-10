package no.nav.k9.los.infrastruktur.abac

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import org.junit.jupiter.api.Test
import java.util.UUID

class PepClientTest {
    @Test
    fun `aktivitetspenger oppgave bruker omraadets PDP og eksplisitt token`() = runBlocking {
        val k9 = mockk<ISifAbacPdpKlient>()
        val aktivitetspenger = mockk<ISifAbacPdpKlient>()
        val graph = mockk<IAzureGraphService>()
        val bruker = TestKontekstFactory.brukerkontekst(Områder.AKTIVITETSPENGER)
        coEvery { aktivitetspenger.harTilgangTilSak(Action.read, SaksnummerDto("123456"), bruker.idToken) } returns true
        val pep = PepClient(graph, SifAbacPdpKlienter(k9, aktivitetspenger))

        pep.harTilgangTilOppgaveV3(oppgave(Områder.AKTIVITETSPENGER), bruker) shouldBe true

        coVerify(exactly = 1) { aktivitetspenger.harTilgangTilSak(Action.read, SaksnummerDto("123456"), bruker.idToken) }
        coVerify { k9 wasNot Called; graph wasNot Called }
    }

    @Test
    fun `kontekst og oppgave maa ha samme omraade foer PDP kalles`() = runBlocking {
        val k9 = mockk<ISifAbacPdpKlient>()
        val aktivitetspenger = mockk<ISifAbacPdpKlient>()
        val pep = PepClient(mockk(), SifAbacPdpKlienter(k9, aktivitetspenger))
        shouldThrow<IllegalArgumentException> {
            pep.harTilgangTilOppgaveV3(oppgave(Områder.K9), TestKontekstFactory.brukerkontekst(Områder.AKTIVITETSPENGER))
        }
        shouldThrow<IllegalArgumentException> {
            pep.harTilgangTilOppgaveV3(oppgave(Områder.K9), Områder.AKTIVITETSPENGER, mockk(), Action.read)
        }
        coVerify { k9 wasNot Called; aktivitetspenger wasNot Called }
    }

    @Test
    fun `aktivitetspenger uten sak eller personer faar ikke lokal fallback`() = runBlocking {
        val aktivitetspenger = mockk<ISifAbacPdpKlient>()
        val oppgave = oppgave(Områder.AKTIVITETSPENGER)
        every { oppgave.hentVerdi(any<String>()) } returns null
        val pep = PepClient(mockk(), SifAbacPdpKlienter(mockk(), aktivitetspenger))

        pep.harTilgangTilOppgaveV3(oppgave, TestKontekstFactory.brukerkontekst(Områder.AKTIVITETSPENGER)) shouldBe false

        coVerify { aktivitetspenger wasNot Called }
    }

    @Test
    fun `annen saksbehandlers grupper sendes til K9 PDP uten lokal gruppeautorisasjon`() = runBlocking {
        val grupper = setOf(UUID.randomUUID())
        val k9 = mockk<ISifAbacPdpKlient>()
        val graph = mockk<IAzureGraphService> {
            coEvery { hentGrupper("Z654321") } returns grupper
        }
        val saksbehandler = mockk<Saksbehandler> { every { navident } returns "Z654321" }
        coEvery { k9.harTilgangTilSak(Action.reserver, SaksnummerDto("123456"), "Z654321", grupper) } returns false
        val pep = PepClient(graph, SifAbacPdpKlienter(k9, mockk()))

        pep.harTilgangTilOppgaveV3(oppgave(Områder.K9), Områder.K9, saksbehandler, Action.reserver) shouldBe false

        coVerify(exactly = 1) { k9.harTilgangTilSak(Action.reserver, SaksnummerDto("123456"), "Z654321", grupper) }
    }

    private fun oppgave(oppgaveområde: Områder) = mockk<Oppgave> {
        every { oppgavetype } returns mockk {
            every { område } returns Område(eksternId = oppgaveområde.eksternId)
            every { eksternId } returns if (oppgaveområde == Områder.K9) "k9sak" else "ungsak"
        }
        every { hentVerdi("saksnummer") } returns "123456"
    }
}
