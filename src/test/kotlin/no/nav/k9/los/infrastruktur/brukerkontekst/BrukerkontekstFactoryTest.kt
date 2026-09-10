package no.nav.k9.los.infrastruktur.brukerkontekst

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenResponse
import no.nav.k9.los.Configuration
import no.nav.k9.los.infrastruktur.abac.*
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test

class BrukerkontekstFactoryTest {
    @Test
    fun `lokal PDP og lokalt token bygger begge konteksttyper uten Azure claims`() = runBlocking {
        val pdp = SifAbacPdpKlientLocal()
        val factory = BrukerkontekstFactory(SifAbacPdpKlienter(pdp, pdp))
        val token = IdTokenLocal()
        token.getTokenId() shouldBe "local"
        Områder.entries.forEach { område ->
            val kontekst = factory.medOmråde(område, token)
            kontekst.idToken shouldBe token
            kontekst.harBasisTilgang shouldBe true
            kontekst.harTilgangTilKode6 shouldBe false
        }
        factory.utenOmråde(token).områderMedBasisTilgang shouldBe Områder.entries.toList()
    }

    @Test
    fun `fabrikken bruker reelle PDP svar og isolerer alle rettigheter per område`() = runBlocking {
        HttpClient(engine(k9 = k9Svar(), ung = ungSvar(del1 = true, oppgavestyring = true, drift = true))).use { http ->
            val factory = factory(http)
            val k9 = factory.medOmråde(Områder.K9, token)
            val aktivitetspenger = factory.medOmråde(Områder.AKTIVITETSPENGER, token)
            k9.harBasisTilgang shouldBe false
            k9.erOppgavestyrer shouldBe false
            k9.harTilgangTilReserveringAvOppgaver shouldBe false
            k9.harDriftstilgang shouldBe false
            aktivitetspenger.harBasisTilgang shouldBe true
            aktivitetspenger.erOppgavestyrer shouldBe true
            aktivitetspenger.harTilgangTilReserveringAvOppgaver shouldBe true
            aktivitetspenger.harDriftstilgang shouldBe true
            aktivitetspenger.idToken shouldBe token
            val alle = factory.utenOmråde(token)
            alle.områderMedBasisTilgang shouldBe listOf(Områder.AKTIVITETSPENGER)
            alle.harBasisTilgangIEttEllerFlereOmråder shouldBe true
            alle.erOppgavestyrerIEttEllerFlereOmråder shouldBe true
            alle.harTilgangTilReserveringAvOppgaverIEttEllerFlereOmråder shouldBe true
            alle.harDriftstilgangIEttEllerFlereOmråder shouldBe true
        }
    }

    @Test
    fun `del1 eller del2 alene er tilstrekkelig og ungdomsprogramrettigheter gir ikke aktivitetspenger`() = runBlocking {
        listOf(false to false, true to false, false to true, true to true).forEach { (del1, del2) ->
            HttpClient(engine(k9Svar(), ungSvar(del1 = del1, del2 = del2))).use { http ->
                val kontekst = factory(http).medOmråde(Områder.AKTIVITETSPENGER, token)
                kontekst.harBasisTilgang shouldBe (del1 || del2)
                kontekst.harTilgangTilReserveringAvOppgaver shouldBe (del1 || del2)
            }
        }
    }

    @Test
    fun `kode6 er global uavhengig av basis og andre områderettigheter`() = runBlocking {
        listOf(false, true).forEach { kode6 ->
            HttpClient(engine(k9Svar(kode6), ungSvar(kode6 = kode6))).use { http ->
                val factory = factory(http)
                Områder.entries.forEach { område ->
                    factory.medOmråde(område, token).harTilgangTilKode6 shouldBe kode6
                }
                factory.utenOmråde(token).harKode6TilgangIEttEllerFlereOmråder shouldBe kode6
            }
        }
    }

    @Test
    fun `PDP feil gir ingen brukerkontekst`() = runBlocking<Unit> {
        HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable, "ikke logg dette") }).use { http ->
            val factory = factory(http)
            shouldThrow<SifAbacPdpHttpException> { factory.medOmråde(Områder.K9, token) }
            shouldThrow<SifAbacPdpHttpException> { factory.utenOmråde(token) }
        }
    }

    @Test
    fun `valgt område kontakter bare valgt PDP selv om det andre er utilgjengelig`() = runBlocking {
        Områder.entries.forEach { område ->
            val valgtSti = if (område == Områder.K9) "/api/k9/nav-ansatt/v2" else "/api/ung/nav-ansatt/v2"
            val stier = mutableListOf<String>()
            HttpClient(MockEngine { request ->
                stier.add(request.url.encodedPath)
                if (request.url.encodedPath == valgtSti) {
                    respond(
                        if (område == Områder.K9) k9Svar(kode6 = true) else ungSvar(kode6 = true),
                        HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respondError(HttpStatusCode.ServiceUnavailable)
                }
            }).use { http ->
                factory(http).medOmråde(område, token).harTilgangTilKode6 shouldBe true
                stier shouldBe listOf(valgtSti)
            }
        }
    }

    @Test
    fun `uten område bruker K9 kode6 uten OR eller sjekk av ulike cachede verdier`() = runBlocking {
        listOf(false, true).forEach { kode6 ->
            HttpClient(engine(k9Svar(kode6), ungSvar(kode6 = !kode6))).use { http ->
                factory(http).utenOmråde(token).harKode6TilgangIEttEllerFlereOmråder shouldBe kode6
            }
        }
    }

    private val token = mockk<IIdToken> {
        every { value } returns "validert-token"
        every { getTokenId() } returns "token-id"
        every { getNavIdent() } returns "Z123456"
    }

    private fun factory(http: HttpClient): BrukerkontekstFactory {
        val configuration = mockk<Configuration> { every { sifAbacPdpUrl() } returns "https://pdp.test" }
        val accessTokenClient = mockk<AccessTokenClient> {
            every { getOnBehalfOfAccessToken(any(), "validert-token") } returns AccessTokenResponse("obo-token", 3600, "Bearer")
        }
        return BrukerkontekstFactory(SifAbacPdpKlienter(
            SifAbacPdpKlientK9(configuration, accessTokenClient, "scope", http),
            SifAbacPdpKlientAktivitetspenger(configuration, accessTokenClient, "scope", http),
        ))
    }

    private fun engine(k9: String, ung: String) = MockEngine { request ->
        request.headers[HttpHeaders.Authorization] shouldBe "Bearer obo-token"
        val svar = when (request.url.encodedPath) {
            "/api/k9/nav-ansatt/v2" -> k9
            "/api/ung/nav-ansatt/v2" -> ung
            else -> error("Uventet PDP-endepunkt")
        }
        respond(svar, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun k9Svar(kode6: Boolean = false) = """
        {"kanVeilede":false, "kanBehandleKode6":$kode6,
         "k9SaksbehandlerTilgang":{"kanSaksbehandle":false}, "kanOppgavestyre":false, "kanDrifte":false}
    """

    private fun ungSvar(
        del1: Boolean = false, del2: Boolean = false, kode6: Boolean = false,
        oppgavestyring: Boolean = false, drift: Boolean = false,
    ) = """
        {"kanVeiledeAktivitetspenger":false, "kanBehandleKode6":$kode6,
         "aktivitetspengerDel1SaksbehandlerTilgang":{"kanSaksbehandle":$del1},
         "aktivitetspengerDel2SaksbehandlerTilgang":{"kanSaksbehandle":$del2},
         "kanOppgavestyreAktivitetspenger":$oppgavestyring, "kanDrifte":$drift,
         "kanVeiledeUngdomsprogramytelse":true, "erUngdomsprogramveileder":true,
         "ungdomsprogramytelseSaksbehandlerTilgang":{"kanSaksbehandle":true}}
    """
}
