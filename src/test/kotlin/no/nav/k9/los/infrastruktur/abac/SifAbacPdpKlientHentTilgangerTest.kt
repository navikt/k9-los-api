package no.nav.k9.los.infrastruktur.abac

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenResponse
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.k9.los.Configuration
import no.nav.k9.los.TestConfiguration
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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

        klient().hentTilganger(idToken) shouldBe Tilganger(
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

        val feil = shouldThrow<SifAbacPdpHttpException> { klient().hentTilganger(idToken) }

        feil.status shouldBe 503
        feil.message shouldBe "Feil ved 'hent-tilganger' mot sif-abac-pdp: HTTP 503"
    }

    private val idToken = mockk<IIdToken> { every { value } returns "validert-innkommende-token" }

    private fun klient() = SifAbacPdpKlient(
        configuration = Configuration(
            MapApplicationConfig(
                *(TestConfiguration.asMap() + ("nav.register_urls.sif_abac_pdp_url" to wireMock.baseUrl() + stiPrefiks))
                    .map { it.key to it.value }
                    .toTypedArray()
            )
        ),
        accessTokenClient = mockk<AccessTokenClient> {
            every { getOnBehalfOfAccessToken(any(), "validert-innkommende-token") } returns
                    AccessTokenResponse("obo-token", 3600, "Bearer")
        },
        scope = "api://dev-fss.k9saksbehandling.sif-abac-pdp/.default",
        httpClient = HttpClient(Java)
    )

    @BeforeEach
    fun nullstillStubber() = WireMock.reset()

    private companion object {
        private const val stiPrefiks = "/sif-abac-pdp-mock"
        private val wireMock: WireMockServer = WireMockBuilder().build()

        @JvmStatic
        @AfterAll
        fun stopp() = wireMock.stop()
    }
}
