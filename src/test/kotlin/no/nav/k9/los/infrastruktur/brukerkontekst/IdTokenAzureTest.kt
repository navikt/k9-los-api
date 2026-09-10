package no.nav.k9.los.infrastruktur.brukerkontekst

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.server.auth.jwt.JWTPrincipal
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.dusseldorf.ktor.auth.UnAuthorizedException
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import org.junit.jupiter.api.Test

class IdTokenAzureTest {
    @Test
    fun `claims inkludert uti hentes fra validert principal ikke dekoding av tokenstrengen`() {
        val token = IdToken.fra("opaque-token", principal("token-id"))
        token.getTokenId() shouldBe "token-id"
        token.getNavIdent() shouldBe "Z123456"
        token.value shouldBe "opaque-token"
    }

    @Test
    fun `manglende eller tom uti avvises saa ulike token ikke deler cache`() {
        listOf(null, "", " ").forEach { uti ->
            shouldThrow<UnAuthorizedException> { IdToken.fra("opaque-token", principal(uti)) }
        }
    }

    private fun principal(uti: String?) = mockk<JWTPrincipal> {
        every { payload.getClaim("uti").asString() } returns uti
        every { payload.getClaim("NAVident").asString() } returns "Z123456"
        every { payload.getClaim("name").asString() } returns "Testbruker"
        every { payload.getClaim("preferred_username").asString() } returns "test@example.com"
    }
}
