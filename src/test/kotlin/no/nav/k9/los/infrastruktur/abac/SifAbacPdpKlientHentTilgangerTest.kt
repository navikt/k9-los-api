package no.nav.k9.los.infrastruktur.abac

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenResponse
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.dusseldorf.testsupport.wiremock.getAzureV2WellKnownUrl
import no.nav.k9.los.Configuration
import no.nav.k9.los.TestConfiguration
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

internal class SifAbacPdpKlientHentTilgangerTest {

    @Test
    fun `henter tilganger med OBO-token og mapper svaret`() = runBlocking<Unit> {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("$stiPrefiks/api/k9/nav-ansatt/v2"))
                .withHeader(HttpHeaders.Authorization, WireMock.equalTo("Bearer obo-token"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "brukernavn": "skal-ignoreres",
                              "navn": "skal-ignoreres",
                              "kanVeilede": false,
                              "kanBehandleKode6": true,
                              "k9SaksbehandlerTilgang": { "kanSaksbehandle": true },
                              "kanOppgavestyre": false,
                              "kanDrifte": true
                            }
                            """.trimIndent()
                        )
                )
        )

        klient().hentTilganger(Områder.K9, idToken) shouldBe Tilganger(
            basis = true,
            kode6 = true,
            oppgavestyring = false,
            reservering = true,
            drift = true,
        )
    }

    @Test
    fun `kaster med statuskode, uten innhold fra responsen, ved feil`() = runBlocking<Unit> {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("$stiPrefiks/api/k9/nav-ansatt/v2"))
                .willReturn(WireMock.aResponse().withStatus(503).withBody("sensitivt innhold"))
        )

        val feil = shouldThrow<SifAbacPdpHttpException> { klient().hentTilganger(Områder.K9, idToken) }

        feil.status shouldBe 503
        feil.message shouldBe "Feil ved 'hent-tilganger' mot sif-abac-pdp: HTTP 503"
    }

    @Test
    fun `avbryter hengende kall og cacher ikke timeout`() = runBlocking<Unit> {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("$stiPrefiks/api/k9/nav-ansatt/v2"))
                .willReturn(WireMock.aResponse().withStatus(200).withFixedDelay(1_000))
        )
        val klient = klient()

        shouldThrow<SifAbacPdpUtilgjengeligException> { klient.hentTilganger(Områder.K9, idToken) }

        WireMock.reset()
        stubGyldigeTilganger()
        klient.hentTilganger(Områder.K9, idToken).basis shouldBe true
    }

    @Test
    fun `ruter eksplisitt område uten request context`() = runBlocking<Unit> {
        val k9 = mockk<ISifAbacPdpKlient>()
        val aktivitetspenger = mockk<ISifAbacPdpKlient>()
        val aktørId = mockk<no.nav.sif.abac.kontrakt.person.AktørId>()
        val saksnummer = mockk<no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto>()
        val grupper = setOf(UUID.randomUUID())
        val klient = SifAbacPdpKlient(k9, aktivitetspenger)

        coEvery { aktivitetspenger.diskresjonskoderPerson(aktørId) } returns emptySet()
        coEvery { aktivitetspenger.harTilgangTilSak(Action.read, saksnummer, idToken) } returns true
        coEvery { k9.harTilgangTilSak(Action.reserver, saksnummer, "Z123456", grupper) } returns true

        klient.diskresjonskoderPerson(Områder.AKTIVITETSPENGER, aktørId) shouldBe emptySet()
        klient.harTilgangTilSak(Områder.AKTIVITETSPENGER, Action.read, saksnummer, idToken) shouldBe true
        klient.harTilgangTilSak(Områder.K9, Action.reserver, saksnummer, "Z123456", grupper) shouldBe true

        coVerify(exactly = 1) { aktivitetspenger.diskresjonskoderPerson(aktørId) }
        coVerify(exactly = 1) { aktivitetspenger.harTilgangTilSak(Action.read, saksnummer, idToken) }
        coVerify(exactly = 1) { k9.harTilgangTilSak(Action.reserver, saksnummer, "Z123456", grupper) }
    }

    private val idToken = mockk<IIdToken> {
        every { value } returns "validert-innkommende-token"
        every { getNavIdent() } returns "brukerident"
        every { getTokenId() } returns "token-id"
        every { jwt } returns mockk {
            every { uti } returns "token-id"
        }
    }

    private fun klient() = SifAbacPdpKlient(
        SifAbacPdpKlientK9(
            configuration = configuration,
            accessTokenClient = mockk<AccessTokenClient> {
                every { getOnBehalfOfAccessToken(any(), "validert-innkommende-token") } returns
                        AccessTokenResponse("obo-token", 3600, "Bearer")
            },
            httpClient = HttpClient(Java),
        ),
        SifAbacPdpKlientAktivitetspenger(
            configuration = configuration,
            accessTokenClient = mockk<AccessTokenClient> {
                every { getOnBehalfOfAccessToken(any(), "validert-innkommende-token") } returns
                        AccessTokenResponse("obo-token", 3600, "Bearer")
            },
            httpClient = HttpClient(Java),
        ),
        hentTilgangerTimeout = 1000.milliseconds
    )

    private fun stubGyldigeTilganger() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("$stiPrefiks/api/k9/nav-ansatt/v2"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "kanVeilede": true,
                              "kanBehandleKode6": false,
                              "k9SaksbehandlerTilgang": { "kanSaksbehandle": false },
                              "kanOppgavestyre": false,
                              "kanDrifte": false
                            }
                            """.trimIndent()
                        )
                )
        )
    }

    @BeforeEach
    fun nullstillStubber() = WireMock.reset()

    private companion object {
        private const val stiPrefiks = "/sif-abac-pdp-mock"
        private val wireMock: WireMockServer = WireMockBuilder().withAzureSupport().build()

        // Configuration henter discovery-dokumentet over HTTP i init. Uten overstyring treffer den
        // 'azure-mock' fra k9-verdikjeden, som kun finnes lokalt (/etc/hosts) og ikke i CI.
        // Bygges under companion-init fordi WireMock.reset() i @BeforeEach fjerner azure-stubbene.
        private val configuration = Configuration(
            MapApplicationConfig(
                *(TestConfiguration.asMap() + mapOf(
                    "nav.register_urls.sif_abac_pdp_url" to wireMock.baseUrl() + stiPrefiks,
                    "nav.auth.clients.0.discovery_endpoint" to wireMock.getAzureV2WellKnownUrl(),
                ))
                    .map { it.key to it.value }
                    .toTypedArray()
            )
        )

        @JvmStatic
        @AfterAll
        fun stopp() = wireMock.stop()
    }
}
