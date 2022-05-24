package no.nav.k9.tjenester.kodeverk

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.contains
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.installContentNegotiation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.junit5.KoinTestExtension

internal class KodeverkApisKtTest : KoinTest {
    @JvmField
    @RegisterExtension
    val koinTestRule = KoinTestExtension.create {
        modules(buildAndTestConfig(mockk()))
    }

    @Test
    fun `skal parse full kodeverk`() {
        withTestApplication(Application::kodeverkApiTestModule) {
            handleRequest(HttpMethod.Get, "/kodeverk").apply {
                assertThat(response.status()).isEqualTo(HttpStatusCode.OK)

                val responseJson = objectMapper().readTree(response.content)
                val fagsakYtelseTyper = responseJson.get(FagsakYtelseType::class.java.simpleName) as ArrayNode
                fagsakYtelseTyper.forEach {
                    assertThat(it.contains("navn")).isTrue()
                }
            }
        }
    }

}

private fun Application.kodeverkApiTestModule() {
    routing {
        KodeverkApis()
    }

    installContentNegotiation()
}
