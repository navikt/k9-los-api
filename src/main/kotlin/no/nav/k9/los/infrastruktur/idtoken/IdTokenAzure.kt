package no.nav.k9.los.infrastruktur.idtoken

import io.ktor.server.auth.jwt.*
import no.nav.helse.dusseldorf.ktor.auth.UnAuthorizedException

data class IdTokenAzure(
    override val value: String,
    private val navIdent: String,
    private val name: String,
    private val preferredUsername: String,
) : IdToken {
    override fun getNavIdent(): String = navIdent
    override fun getName(): String = name
    override fun getPreferredUsername(): String = preferredUsername

    companion object {
        fun fra(value: String, principal: JWTPrincipal) = IdTokenAzure(
            value = value,
            navIdent = principal.payload.getClaim("NAVident").asString()
                ?: throw UnAuthorizedException(),
            name = principal.payload.getClaim("name").asString()
                ?: throw UnAuthorizedException(),
            preferredUsername = principal.payload.getClaim("preferred_username").asString()
                ?: throw UnAuthorizedException(),
        )
    }
}
