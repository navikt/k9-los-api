package no.nav.k9.los.testsupport.wiremock

import com.github.tomakehurst.wiremock.WireMockServer

internal object Paths {
    private const val AZURE_V1_PATH = "/azure/v1.0"
    internal const val AZURE_V1_TOKEN_PATH = "$AZURE_V1_PATH/token"
    internal const val AZURE_V1_WELL_KNOWN_PATH = "$AZURE_V1_PATH/.well-known/openid-configuration"
    internal const val AZURE_V1_JWKS_PATH = "$AZURE_V1_PATH/jwks"
    internal const val AZURE_V1_AUTHORIZATION_PATH = "$AZURE_V1_PATH/authorize"

    private const val AZURE_V2_PATH = "/azure/v2.0"
    internal const val AZURE_V2_TOKEN_PATH = "$AZURE_V2_PATH/token"
    internal const val AZURE_V2_WELL_KNOWN_PATH = "$AZURE_V2_PATH/.well-known/openid-configuration"
    internal const val AZURE_V2_JWKS_PATH = "$AZURE_V2_PATH/jwks"
    internal const val AZURE_V2_AUTHORIZATION_PATH = "$AZURE_V2_PATH/authorize"
}

fun WireMockServer.getAzureV1WellKnownUrl() = baseUrl() + Paths.AZURE_V1_WELL_KNOWN_PATH
fun WireMockServer.getAzureV1TokenUrl() = baseUrl() + Paths.AZURE_V1_TOKEN_PATH
fun WireMockServer.getAzureV1JwksUrl() = baseUrl() + Paths.AZURE_V1_JWKS_PATH
fun WireMockServer.getAzureV1AuthorizationUrl() = baseUrl() + Paths.AZURE_V1_AUTHORIZATION_PATH

fun WireMockServer.getAzureV2WellKnownUrl() = baseUrl() + Paths.AZURE_V2_WELL_KNOWN_PATH
fun WireMockServer.getAzureV2TokenUrl() = baseUrl() + Paths.AZURE_V2_TOKEN_PATH
fun WireMockServer.getAzureV2JwksUrl() = baseUrl() + Paths.AZURE_V2_JWKS_PATH
fun WireMockServer.getAzureV2AuthorizationUrl() = baseUrl() + Paths.AZURE_V2_AUTHORIZATION_PATH
