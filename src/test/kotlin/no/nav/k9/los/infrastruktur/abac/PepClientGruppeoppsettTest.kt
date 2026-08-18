package no.nav.k9.los.infrastruktur.abac

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.rest.CoroutineRequestContext
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
    private val k9Drift = UUID.randomUUID()
    private val ungDrift = UUID.randomUUID()
    private val k9Kode6 = UUID.randomUUID()
    private val ungKode6 = UUID.randomUUID()

    private val oppsett = Gruppeoppsett(
        k9 = GrupperForOmråde(k9Saksbehandler, k9Veileder, k9Oppgavestyrer, k9Drift, k9Kode6),
        ung = GrupperForOmråde(ungSaksbehandler, ungVeileder, ungOppgavestyrer, ungDrift, ungKode6),
    )

    @Test
    fun `bruker separate saksbehandlergrupper for K9 og UNG`() {
        runBlocking {
            val pepClient = pepClient()
            val token = token(setOf(ungSaksbehandler))

            medContext(token, Områder.K9) { pepClient.harBasisTilgang() } shouldBe false
            medContext(token, Områder.UNG) { pepClient.harBasisTilgang() } shouldBe true
        }
    }

    @Test
    fun `bruker separate tokenbaserte grupper for alle roller`() {
        runBlocking {
            val pepClient = pepClient()
            val ungGrupper = listOf(ungSaksbehandler, ungVeileder, ungOppgavestyrer, ungDrift, ungKode6)

            for (gruppe in ungGrupper) {
                val token = token(setOf(gruppe))
                medContext(token, Områder.K9) { harRolle(pepClient, gruppe) } shouldBe false
                medContext(token, Områder.UNG) { harRolle(pepClient, gruppe) } shouldBe true
            }
        }
    }

    @Test
    fun `bruker separat kode6-gruppe ved oppslag av annen saksbehandler`() {
        runBlocking {
            val azureGraphService = mockk<IAzureGraphService>()
            coEvery { azureGraphService.hentGrupperForSaksbehandler("Z999999") } returns setOf(ungKode6)
            val pepClient = pepClient(azureGraphService)

            medContext(token(emptySet()), Områder.K9) { pepClient.harTilgangTilKode6("Z999999") } shouldBe false
            medContext(token(emptySet()), Områder.UNG) { pepClient.harTilgangTilKode6("Z999999") } shouldBe true
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

    private suspend fun harRolle(pepClient: PepClient, gruppe: UUID): Boolean = when (gruppe) {
        ungSaksbehandler -> pepClient.harTilgangTilReserveringAvOppgaver()
        ungVeileder -> pepClient.harBasisTilgang()
        ungOppgavestyrer -> pepClient.erOppgaveStyrer()
        ungDrift -> pepClient.kanLeggeUtDriftsmelding()
        ungKode6 -> pepClient.harTilgangTilKode6()
        else -> error("Ukjent testgruppe")
    }

    private suspend fun <T> medContext(token: IdToken, område: Områder, block: suspend () -> T): T =
        withContext(CoroutineRequestContext(token, område)) { block() }
}
