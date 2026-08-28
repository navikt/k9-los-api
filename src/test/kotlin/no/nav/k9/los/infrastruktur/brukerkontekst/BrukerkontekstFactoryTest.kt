package no.nav.k9.los.infrastruktur.brukerkontekst

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.abac.Action
import no.nav.k9.los.infrastruktur.abac.ISifAbacPdpKlient
import no.nav.k9.los.infrastruktur.abac.PepClient
import no.nav.k9.los.infrastruktur.abac.SifAbacPdpKlienter
import no.nav.k9.los.infrastruktur.abac.tilganger.OmrådeTilganger
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import org.junit.jupiter.api.Test
import java.util.*

class BrukerkontekstFactoryTest {

    private fun tilganger(
        harBasisTilgang: Boolean = false,
        harTilgangTilKode6: Boolean = false,
        erOppgavestyrer: Boolean = false,
        harTilgangTilReserveringAvOppgaver: Boolean = false,
        kanLeggeUtDriftsmelding: Boolean = false,
    ) = OmrådeTilganger(
        harBasisTilgang = harBasisTilgang,
        harTilgangTilKode6 = harTilgangTilKode6,
        erOppgavestyrer = erOppgavestyrer,
        harTilgangTilReserveringAvOppgaver = harTilgangTilReserveringAvOppgaver,
        kanLeggeUtDriftsmelding = kanLeggeUtDriftsmelding,
    )

    @Test
    fun `tilganger fra PDP gjelder per område`() {
        runBlocking {
            val kontekstK9 = kontekst(Områder.K9, tilganger())
            val kontekstAktivitetspenger = kontekst(Områder.AKTIVITETSPENGER, tilganger(harBasisTilgang = true))

            kontekstK9.harBasisTilgang shouldBe false
            kontekstAktivitetspenger.harBasisTilgang shouldBe true
        }
    }

    @Test
    fun `alle tilganger mappes fra PDP per område`() {
        runBlocking {
            val full = tilganger(
                harBasisTilgang = true,
                harTilgangTilKode6 = true,
                erOppgavestyrer = true,
                harTilgangTilReserveringAvOppgaver = true,
                kanLeggeUtDriftsmelding = true,
            )
            val kontekst = kontekst(Områder.AKTIVITETSPENGER, full)

            kontekst.harBasisTilgang shouldBe true
            kontekst.harTilgangTilKode6 shouldBe true
            kontekst.erOppgavestyrer shouldBe true
            kontekst.harTilgangTilReserveringAvOppgaver shouldBe true
            kontekst.harDriftstilgang shouldBe true

            val tom = kontekst(Områder.K9, tilganger())
            tom.harBasisTilgang shouldBe false
            tom.harTilgangTilKode6 shouldBe false
            tom.erOppgavestyrer shouldBe false
            tom.harTilgangTilReserveringAvOppgaver shouldBe false
            tom.harDriftstilgang shouldBe false
        }
    }

    @Test
    fun `driftstilgang er per område, og utenOmråde gir true hvis minst ett område kan drifte`() {
        runBlocking {
            val kunK9Drift = TestKontekstFactory.brukerkontekstUtenOmråde(
                tilgangerPerOmråde = mapOf(
                    Områder.K9 to tilganger(kanLeggeUtDriftsmelding = true),
                    Områder.AKTIVITETSPENGER to tilganger(),
                )
            )
            kunK9Drift.harDriftstilgangIEttEllerFlereOmråder shouldBe true

            val ingenDrift = TestKontekstFactory.brukerkontekstUtenOmråde(
                tilgangerPerOmråde = mapOf(
                    Områder.K9 to tilganger(),
                    Områder.AKTIVITETSPENGER to tilganger(),
                )
            )
            ingenDrift.harDriftstilgangIEttEllerFlereOmråder shouldBe false
        }
    }

    @Test
    fun `utenOmråde aggregerer basis- og kode6-tilgang på tvers av områder`() {
        runBlocking {
            val kontekst = TestKontekstFactory.brukerkontekstUtenOmråde(
                tilgangerPerOmråde = mapOf(
                    Områder.K9 to tilganger(harTilgangTilKode6 = true),
                    Områder.AKTIVITETSPENGER to tilganger(harBasisTilgang = true),
                )
            )
            kontekst.harBasisTilgangIEttEllerFlereOmråder shouldBe true
            kontekst.harKode6TilgangIEttEllerFlereOmråder shouldBe true

            val ingen = TestKontekstFactory.brukerkontekstUtenOmråde(
                tilgangerPerOmråde = mapOf(
                    Områder.K9 to tilganger(),
                    Områder.AKTIVITETSPENGER to tilganger(),
                )
            )
            ingen.harBasisTilgangIEttEllerFlereOmråder shouldBe false
            ingen.harKode6TilgangIEttEllerFlereOmråder shouldBe false
        }
    }

    @Test
    fun `oppgavetilgang slår opp grupper via Graph, ikke fra kontekst`() {
        runBlocking {
            val azureGraphService = mockk<IAzureGraphService>()
            val pdpKlient = mockk<ISifAbacPdpKlient>()
            val pdpKlienter = mockk<SifAbacPdpKlienter>()
            val oppgave = mockk<Oppgave>(relaxed = true)
            val brukergrupper = setOf(UUID.randomUUID())
            every { pdpKlienter.forOmråde(Områder.K9) } returns pdpKlient
            every { oppgave.oppgavetype.eksternId } returns "k9sak"
            every { oppgave.oppgavetype.område.tilOmrådeEnum() } returns Områder.K9
            every { oppgave.hentVerdi("saksnummer") } returns "123"
            coEvery { azureGraphService.hentGrupper("Z123456") } returns brukergrupper
            coEvery {
                pdpKlient.harTilgangTilSak(Action.read, any(), "Z123456", brukergrupper)
            } returns true

            PepClient(
                azureGraphService,
                pdpKlienter,
                mockk(),
                mockk(),
                "",
                mockk(),
            ).harTilgangTilOppgaveV3(oppgave, kontekst(Områder.K9, tilganger())) shouldBe true

            coVerify(exactly = 1) { azureGraphService.hentGrupper("Z123456") }
        }
    }

    private fun token() = mockk<IdToken> {
        coEvery { getNavIdent() } returns "Z123456"
    }

    private fun kontekst(område: Områder, tilganger: OmrådeTilganger) =
        TestKontekstFactory.brukerkontekst(område, token(), tilganger)
}
