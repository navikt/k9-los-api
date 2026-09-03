package no.nav.k9.los.infrastruktur.brukerkontekst

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.abac.AktivitetspengerGrupper
import no.nav.k9.los.infrastruktur.abac.Gruppeoppsett
import no.nav.k9.los.infrastruktur.abac.K9Grupper
import no.nav.k9.los.infrastruktur.abac.tilganger.OmrådeTilganger
import no.nav.k9.los.infrastruktur.abac.tilganger.PdpTilgangsskygge
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import org.junit.jupiter.api.Test
import java.util.UUID

class BrukerkontekstFactoryTest {

    @Test
    fun `gruppe-claims er autoritative for K9 selv om PDP observeres`() = runBlocking {
        val skygge = mockk<PdpTilgangsskygge>(relaxed = true)
        val factory = BrukerkontekstFactory(
            gruppeoppsett = gruppeoppsett(),
            pdpTilgangsskygge = skygge,
        )

        val kontekst = factory.medOmråde(Områder.K9, token(emptySet()))

        kontekst.harBasisTilgang shouldBe false
        kontekst.harTilgangTilKode6 shouldBe false
        kontekst.erOppgavestyrer shouldBe false
        kontekst.harTilgangTilReserveringAvOppgaver shouldBe false
        kontekst.harDriftstilgang shouldBe false
        verify(exactly = 1) { skygge.observer(any(), Tilganger(false, false, false, false, false)) }
    }

    @Test
    fun `Aktivitetspenger bruker grupper uten å kalle PDP-skyggen`() = runBlocking {
        val aktivitetspengerSaksbehandler = UUID.randomUUID()
        val skygge = mockk<PdpTilgangsskygge>(relaxed = true)
        val factory = BrukerkontekstFactory(
            gruppeoppsett = gruppeoppsett(aktivitetspengerSaksbehandler),
            pdpTilgangsskygge = skygge,
        )

        val kontekst = factory.medOmråde(
            Områder.AKTIVITETSPENGER,
            token(setOf(aktivitetspengerSaksbehandler.toString())),
        )

        kontekst.harBasisTilgang shouldBe true
        kontekst.harTilgangTilReserveringAvOppgaver shouldBe true
        verify(exactly = 0) { skygge.observer(any(), any()) }
    }

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
    fun `utenOmråde aggregerer tilganger på tvers av områder`() {
        runBlocking {
            val kontekst = TestKontekstFactory.brukerkontekstUtenOmråde(
                tilgangerPerOmråde = mapOf(
                    Områder.K9 to tilganger(harTilgangTilKode6 = true, erOppgavestyrer = true),
                    Områder.AKTIVITETSPENGER to tilganger(
                        harBasisTilgang = true,
                        harTilgangTilReserveringAvOppgaver = true,
                    ),
                )
            )
            kontekst.harBasisTilgangIEttEllerFlereOmråder shouldBe true
            kontekst.områderMedBasisTilgang shouldBe listOf(Områder.AKTIVITETSPENGER)
            kontekst.harKode6TilgangIEttEllerFlereOmråder shouldBe true
            kontekst.erOppgavestyrerIEttEllerFlereOmråder shouldBe true
            kontekst.harTilgangTilReserveringAvOppgaverIEttEllerFlereOmråder shouldBe true

            val ingen = TestKontekstFactory.brukerkontekstUtenOmråde(
                tilgangerPerOmråde = mapOf(
                    Områder.K9 to tilganger(),
                    Områder.AKTIVITETSPENGER to tilganger(),
                )
            )
            ingen.harBasisTilgangIEttEllerFlereOmråder shouldBe false
            ingen.områderMedBasisTilgang shouldBe emptyList()
            ingen.harKode6TilgangIEttEllerFlereOmråder shouldBe false
            ingen.erOppgavestyrerIEttEllerFlereOmråder shouldBe false
            ingen.harTilgangTilReserveringAvOppgaverIEttEllerFlereOmråder shouldBe false
        }
    }

    private fun token(groups: Set<String> = emptySet()) = mockk<IdToken> {
        coEvery { getNavIdent() } returns "Z123456"
        every { this@mockk.groups } returns groups
    }

    private fun kontekst(område: Områder, tilganger: OmrådeTilganger) =
        TestKontekstFactory.brukerkontekst(område, token(), tilganger)

    private fun gruppeoppsett(aktivitetspengerSaksbehandler: UUID? = UUID.randomUUID()) = Gruppeoppsett(
        k9 = K9Grupper(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
        aktivitetspenger = AktivitetspengerGrupper(
            saksbehandlerNavkontor = aktivitetspengerSaksbehandler,
            saksbehandlerNay = UUID.randomUUID(),
            oppgavestyrer = UUID.randomUUID(),
            kode6 = UUID.randomUUID(),
        ),
        drift = UUID.randomUUID(),
    )
}
