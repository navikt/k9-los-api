package no.nav.k9.los.infrastruktur.idtoken

import io.ktor.server.auth.jwt.JWTPrincipal
import no.nav.helse.dusseldorf.ktor.auth.UnAuthorizedException

data class IdTokenAzure(
    override val value: String,
    override val groups: Set<String>,
    private val navIdent: String,
    private val name: String,
    private val username: String,
) : IdToken {
    override fun getNavIdent(): String = navIdent
    override fun getName(): String = name
    override fun getUsername(): String = username

    companion object {
        fun fra(value: String, principal: JWTPrincipal) = IdTokenAzure(
            value = value,
            groups = principal.payload.getClaim("groups").asList(String::class.java)?.toSet().orEmpty(),
            navIdent = principal.payload.getClaim("NAVident").asString()
                ?: throw UnAuthorizedException(),
            name = principal.payload.getClaim("name").asString()
                ?: throw UnAuthorizedException(),
            username = principal.payload.getClaim("preferred_username").asString()
                ?: throw UnAuthorizedException(),
        )
    }
}
