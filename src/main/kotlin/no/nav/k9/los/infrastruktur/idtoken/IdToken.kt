package no.nav.k9.los.infrastruktur.idtoken

import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import java.nio.charset.Charset
import java.util.*

data class IdToken(
    override val value: String,
) : IIdToken {
    override val jwt: JWTToken = try {
        val split = value.split(".")
        val header = String(Base64.getDecoder().decode(split[0]), Charset.defaultCharset())
        val body = String(Base64.getDecoder().decode(split[1]), Charset.defaultCharset())
        LosObjectMapper.instance.readValue(body, JWTToken::class.java)
    } catch (cause: Throwable) {
        throw IdTokenInvalidFormatException(this, cause)
    }

    override fun getNavIdent(): String = jwt.NAVident
    override fun getName(): String = jwt.name
    override fun getUsername(): String = jwt.preferredUsername

}

internal fun ApplicationCall.idToken(): IdToken {
    val jwt = request.parseAuthorizationHeader()?.render() ?: throw IllegalStateException("Token ikke satt")
    return IdToken(jwt.substringAfter("Bearer "))
}
