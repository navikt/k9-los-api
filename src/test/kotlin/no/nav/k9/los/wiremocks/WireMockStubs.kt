package no.nav.k9.los.wiremocks

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import io.ktor.http.HttpHeaders
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder

private const val tpsProxyServerPath = "/tps-proxy-mock"

internal fun WireMockBuilder.k9SelvbetjeningOppslagConfig() = wireMockConfiguration {
    it
        .extensions(TpsProxyResponseTransformer())
}


internal fun WireMockServer.stubTpsProxyGetPerson(): WireMockServer {
    WireMock.stubFor(
        WireMock.get(WireMock.urlPathMatching("$tpsProxyServerPath/innsyn/person*"))
            .withHeader(HttpHeaders.Authorization, AnythingPattern())
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withStatus(200)
                    .withTransformers("tps-proxy-person")
            )
    )
    return this
}

internal fun WireMockServer.stubTpsProxyGetNavn(): WireMockServer {
    WireMock.stubFor(
        WireMock.get(WireMock.urlPathMatching("$tpsProxyServerPath/navn"))
            .withHeader(HttpHeaders.Authorization, AnythingPattern())
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withStatus(200)
                    .withBody(
                        """  
                            {
                                "fornavn": "KLØKTIG",
                                "mellomnavn": "BLUNKENDE",
                                "etternavn": "SUPERKONSOLL"
                            }
                        """.trimIndent()
                    )
            )
    )
    return this
}

internal fun WireMockServer.stubTpsProxyGetBarn(): WireMockServer {
    WireMock.stubFor(
        WireMock.get(WireMock.urlPathMatching("$tpsProxyServerPath/innsyn/barn*"))
            .withHeader(HttpHeaders.Authorization, AnythingPattern())
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withStatus(200)
                    .withTransformers("tps-proxy-barn")
            )
    )
    return this
}

internal fun WireMockServer.getTpsProxyUrl() = baseUrl() + tpsProxyServerPath
