package no.nav.k9.los.testsupport.jws

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.SigningKeyNotFoundException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class StaticJwkProvider(jwksJson: String) : JwkProvider {
    private val jwks: Map<String, Jwk>

    init {
        val mapper = jacksonObjectMapper()
        val jwksMap = mapper.readValue<Map<String, Any>>(jwksJson)
        @Suppress("UNCHECKED_CAST")
        val keys = jwksMap["keys"] as List<Map<String, Any>>
        jwks = keys.associate { keyMap ->
            val jwk = Jwk.fromValues(keyMap)
            jwk.id to jwk
        }
    }

    override fun get(keyId: String): Jwk {
        return jwks[keyId] ?: throw SigningKeyNotFoundException("No key found for kid: $keyId", null)
    }
}
