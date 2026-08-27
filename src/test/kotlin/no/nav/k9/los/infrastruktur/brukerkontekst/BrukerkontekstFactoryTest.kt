package no.nav.k9.los.infrastruktur.brukerkontekst

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.abac.AktivitetspengerGrupper
import no.nav.k9.los.infrastruktur.abac.Gruppeoppsett
import no.nav.k9.los.infrastruktur.abac.K9Grupper
import no.nav.k9.los.infrastruktur.abac.PepClient
import no.nav.k9.los.infrastruktur.abac.Action
import no.nav.k9.los.infrastruktur.abac.ISifAbacPdpKlient
import no.nav.k9.los.infrastruktur.abac.SifAbacPdpKlienter
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import org.junit.jupiter.api.Test
import java.util.UUID

class BrukerkontekstFactoryTest {
    private val k9Saksbehandler = UUID.randomUUID()
    private val aktivitetspengerLokalkontor = UUID.randomUUID()
    private val aktivitetspengerNay = UUID.randomUUID()
    private val k9Veileder = UUID.randomUUID()
    private val k9Oppgavestyrer = UUID.randomUUID()
    private val aktivitetspengerOppgavestyrer = UUID.randomUUID()
    private val drift = UUID.randomUUID()
    private val k9Kode6 = UUID.randomUUID()
    private val aktivitetspengerKode6 = UUID.randomUUID()

    private val oppsett = Gruppeoppsett(
        k9 = K9Grupper(k9Saksbehandler, k9Veileder, k9Oppgavestyrer, k9Kode6),
        aktivitetspenger = AktivitetspengerGrupper(
            aktivitetspengerLokalkontor,
            aktivitetspengerNay,
            aktivitetspengerOppgavestyrer,
            aktivitetspengerKode6
        ),
        drift = drift,
    )

    @Test
    fun `bruker separate saksbehandlergrupper for K9 og AKTIVITETSPENGER`() {
        runBlocking {
            val token = token(setOf(aktivitetspengerLokalkontor))

            kontekst(token, Områder.K9).harBasisTilgang shouldBe false
            kontekst(token, Områder.AKTIVITETSPENGER).harBasisTilgang shouldBe true
        }
    }

    @Test
    fun `aktivitetspenger gir basistilgang for både lokalkontor og nay`() {
        runBlocking {
            val lokalkontorToken = token(setOf(aktivitetspengerLokalkontor))
            val nayToken = token(setOf(aktivitetspengerNay))

            kontekst(lokalkontorToken, Områder.AKTIVITETSPENGER).harBasisTilgang shouldBe true
            kontekst(nayToken, Områder.AKTIVITETSPENGER).harBasisTilgang shouldBe true
        }
    }

    @Test
    fun `aktivitetspenger gir reserveringstilgang for både lokalkontor og nay`() {
        runBlocking {
            val lokalkontorToken = token(setOf(aktivitetspengerLokalkontor))
            val nayToken = token(setOf(aktivitetspengerNay))

            kontekst(lokalkontorToken, Områder.AKTIVITETSPENGER).harTilgangTilReserveringAvOppgaver shouldBe true
            kontekst(nayToken, Områder.AKTIVITETSPENGER).harTilgangTilReserveringAvOppgaver shouldBe true
        }
    }

    @Test
    fun `bruker separate tokenbaserte grupper for alle roller`() {
        runBlocking {
            val aktivitetspengerGrupper = listOf(
                aktivitetspengerLokalkontor,
                aktivitetspengerNay,
                aktivitetspengerOppgavestyrer,
                aktivitetspengerKode6
            )

            for (gruppe in aktivitetspengerGrupper) {
                val token = token(setOf(gruppe))
                harRolle(gruppe, kontekst(token, Områder.K9)) shouldBe false
                harRolle(gruppe, kontekst(token, Områder.AKTIVITETSPENGER)) shouldBe true
            }
        }
    }

    @Test
    fun `drift-gruppen er global og gir tilgang til driftsmeldinger uavhengig av område`() {
        runBlocking {
            val token = token(setOf(drift))

            globalKontekst(token).kanLeggeUtDriftsmelding shouldBe true
        }
    }

    @Test
    fun `bruker separat kode6-gruppe ved oppslag av annen saksbehandler`() {
        runBlocking {
            val azureGraphService = mockk<IAzureGraphService>()
            coEvery { azureGraphService.hentGrupper("Z999999") } returns setOf(aktivitetspengerKode6)
            val pepClient = pepClient(azureGraphService)

            pepClient.harSaksbehandlerTilgangTilKode6("Z999999", kontekst(token(emptySet()), Områder.K9)) shouldBe false
            pepClient.harSaksbehandlerTilgangTilKode6("Z999999", kontekst(token(emptySet()), Områder.AKTIVITETSPENGER)) shouldBe true
        }
    }

    @Test
    fun `bruker grupper fra kontekst uten Graph-oppslag ved oppgavetilgang`() {
        runBlocking {
            val azureGraphService = mockk<IAzureGraphService>()
            val pdpKlient = mockk<ISifAbacPdpKlient>()
            val pdpKlienter = mockk<SifAbacPdpKlienter>()
            val oppgave = mockk<Oppgave>(relaxed = true)
            val brukergrupper = setOf(k9Saksbehandler, k9Kode6)
            val kontekst = kontekst(token(brukergrupper), Områder.K9)
            every { pdpKlienter.forOmråde(Områder.K9) } returns pdpKlient
            every { oppgave.oppgavetype.eksternId } returns "k9sak"
            every { oppgave.hentVerdi("saksnummer") } returns "123"
            coEvery {
                pdpKlient.harTilgangTilSak(Action.read, any(), "Z123456", brukergrupper)
            } returns true

            PepClient(azureGraphService, pdpKlienter, oppsett)
                .harTilgangTilOppgaveV3(oppgave, kontekst) shouldBe true

            coVerify(exactly = 0) { azureGraphService.hentGrupper(any<String>()) }
        }
    }

    private fun pepClient(azureGraphService: IAzureGraphService = mockk()) = PepClient(
        azureGraphService = azureGraphService,
        sifAbacPdpKlienter = mockk(),
        gruppeoppsett = oppsett,
    )

    private fun token(grupper: Set<UUID>) = mockk<IdToken> {
        coEvery { groups } returns grupper.map(UUID::toString).toSet()
        coEvery { getNavIdent() } returns "Z123456"
    }

    private fun harRolle(gruppe: UUID, brukerkontekst: BrukerkontekstMedOmråde): Boolean = when (gruppe) {
        aktivitetspengerLokalkontor -> brukerkontekst.harTilgangTilReserveringAvOppgaver
        aktivitetspengerNay -> brukerkontekst.harTilgangTilReserveringAvOppgaver
        aktivitetspengerOppgavestyrer -> brukerkontekst.erOppgavestyrer
        aktivitetspengerKode6 -> brukerkontekst.harTilgangTilKode6
        else -> error("Ukjent testgruppe")
    }

    private fun kontekst(token: IdToken, område: Områder) =
        TestKontekstFactory.brukerkontekst(område, token, gruppeoppsett = oppsett, lokaleTilganger = false)

    private fun globalKontekst(token: IdToken) =
        TestKontekstFactory.brukerkontekstUtenOmråde(token, gruppeoppsett = oppsett, lokaleTilganger = false)
}
