package no.nav.k9.los.infrastruktur.abac

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.kontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.kontekst.TestKontekstFactory
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import java.util.UUID

class PepClientGruppeoppsettTest {
    private val k9Saksbehandler = UUID.randomUUID()
    private val ungSaksbehandler = UUID.randomUUID()
    private val k9Veileder = UUID.randomUUID()
    private val ungVeileder = UUID.randomUUID()
    private val k9Oppgavestyrer = UUID.randomUUID()
    private val ungOppgavestyrer = UUID.randomUUID()
    private val drift = UUID.randomUUID()
    private val k9Kode6 = UUID.randomUUID()
    private val ungKode6 = UUID.randomUUID()

    private val oppsett = Gruppeoppsett(
        k9 = GrupperForOmråde(k9Saksbehandler, k9Veileder, k9Oppgavestyrer, k9Kode6),
        ung = GrupperForOmråde(ungSaksbehandler, ungVeileder, ungOppgavestyrer, ungKode6),
        drift = drift,
    )

    @Test
    fun `bruker separate saksbehandlergrupper for K9 og UNG`() {
        runBlocking {
            val pepClient = pepClient()
            val token = token(setOf(ungSaksbehandler))

            pepClient.harBasisTilgang(kontekst(token, Områder.K9)) shouldBe false
            pepClient.harBasisTilgang(kontekst(token, Områder.UNG)) shouldBe true
        }
    }

    @Test
    fun `bruker separate tokenbaserte grupper for alle roller`() {
        runBlocking {
            val pepClient = pepClient()
            val ungGrupper = listOf(ungSaksbehandler, ungVeileder, ungOppgavestyrer, ungKode6)

            for (gruppe in ungGrupper) {
                val token = token(setOf(gruppe))
                harRolle(pepClient, gruppe, kontekst(token, Områder.K9)) shouldBe false
                harRolle(pepClient, gruppe, kontekst(token, Områder.UNG)) shouldBe true
            }
        }
    }

    @Test
    fun `drift-gruppen er global og gir tilgang til driftsmeldinger uavhengig av område`() {
        runBlocking {
            val pepClient = pepClient()
            val token = token(setOf(drift))

            pepClient.kanLeggeUtDriftsmelding(globalKontekst(token)) shouldBe true
        }
    }

    @Test
    fun `bruker separat kode6-gruppe ved oppslag av annen saksbehandler`() {
        runBlocking {
            val azureGraphService = mockk<IAzureGraphService>()
            coEvery { azureGraphService.hentGrupper("Z999999") } returns setOf(ungKode6)
            val pepClient = pepClient(azureGraphService)

            pepClient.harTilgangTilKode6("Z999999", kontekst(token(emptySet()), Områder.K9)) shouldBe false
            pepClient.harTilgangTilKode6("Z999999", kontekst(token(emptySet()), Områder.UNG)) shouldBe true
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

    private suspend fun harRolle(pepClient: PepClient, gruppe: UUID, kontekst: BrukerkontekstMedOmråde): Boolean = when (gruppe) {
        ungSaksbehandler -> pepClient.harTilgangTilReserveringAvOppgaver(kontekst)
        ungVeileder -> pepClient.harBasisTilgang(kontekst)
        ungOppgavestyrer -> pepClient.erOppgaveStyrer(kontekst)
        ungKode6 -> pepClient.harTilgangTilKode6(kontekst)
        else -> error("Ukjent testgruppe")
    }

    private fun kontekst(token: IdToken, område: Områder) = TestKontekstFactory.brukerkontekst(område, token)

    private fun globalKontekst(token: IdToken) = TestKontekstFactory.brukerkontekstUtenOmråde(token)
}
