package no.nav.k9.los.oauth2.client

interface AccessTokenClient {
    fun getAccessToken(scopes: Set<String>, onBehalfOf: String) : AccessTokenResponse
    fun getAccessToken(scopes: Set<String>) : AccessTokenResponse
}

data class AccessTokenResponse(
        val accessToken : String,
        val expiresIn: Long,
        val tokenType: String
)
