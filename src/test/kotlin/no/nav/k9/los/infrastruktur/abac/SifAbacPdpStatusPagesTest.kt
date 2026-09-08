package no.nav.k9.los.infrastruktur.abac

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

internal class SifAbacPdpStatusPagesTest {
    @Test
    fun `timeout mot PDP gir service unavailable`() = testApplication {
        application {
            install(StatusPages) {
                sifAbacPdpStatusPages()
            }
            routing {
                get("/beskyttet") {
                    throw SifAbacPdpUtilgjengeligException()
                }
            }
        }

        client.get("/beskyttet").status shouldBe HttpStatusCode.ServiceUnavailable
    }
}
