package no.nav.k9.los

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.helse.dusseldorf.testsupport.jws.ClientCredentials
import no.nav.k9.los.wiremocks.getTpsProxyUrl
import org.json.JSONObject

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        port: Int = 8020,
        tpsProxyBaseUrl: String? = wireMockServer?.getTpsProxyUrl()
    ): Map<String, String> {
        val map = mutableMapOf(
            Pair("ktor.deployment.port", "$port"),
            Pair("nav.register_urls.tps_proxy_v1", "$tpsProxyBaseUrl"),
            Pair("nav.register_urls.pdl_url", "$tpsProxyBaseUrl")
        )

        map["nav.register_urls.k9_url"] = "http://localhost:8080"
        map["nav.register_urls.k9_klage_url"] = "http://localhost:8080"
        map["nav.register_urls.k9_frontend_url"] = "http://localhost:9000"
        map["nav.register_urls.k9_punsj_frontend_url"] = "http://localhost:8080"
        map["nav.register_urls.sif_abac_pdp_url"] = "http://localhost:8913"
        map["nav.audit.enabled"] = "false"
        map["nav.audit.vendor"] = "test"
        map["nav.audit.product"] = "test"
        map["nav.auth.trustore.path"] = "vtp"
        map["nav.auth.trustore.password"] = "vtp"

        map["nav.auth.clients.size"] = "1"
        map["nav.auth.clients.0.alias"] = "azure-v2"
        map["nav.auth.clients.0.client_id"] = "k9-los"
        map["nav.auth.clients.0.private_key_jwk"] = ClientCredentials.ClientA.privateKeyJwk
        map["nav.auth.clients.0.certificate_hex_thumbprint"] = ClientCredentials.ClientA.certificateHexThumbprint
        map["nav.auth.clients.0.discovery_endpoint"] = "http://azure-mock:8100/v2.0/.well-known/openid-configuration"

        map["nav.kafka.bootstrap_servers"] = "vtp:9092"
        map["nav.kafka.username"] = "vtp"
        map["nav.kafka.password"] = "vtp"
        map["nav.kafka.unready_after_stream_stopped_in.amount"] = "1010"
        map["nav.kafka.unready_after_stream_stopped_in.unit"] = "SECONDS"
        map["nav.trust_store.path"] = "${System.getProperty("user.home")}/.modig/truststore.jks"
        map["nav.trust_store.password"] = "changeit"

        map["nav.kafka.statistikkSakTopic"] = "privat-k9statistikk-sak-v1"

        map["nav.db.url"] = "jdbc:postgresql://localhost:5432/k9los "
        map["nav.db.username"] = "k9los"
        map["nav.db.password"] = "k9los"
        map["nav.features.nyOppgavestyring"] = "true"
        map["nav.features.nyOppgavestyringRestApi"] = "true"
        map["nav.kafka.åpenStatistikkBehandlingTopic"] = "aapen-k9statistikk-behandling-v2"
        map["nav.kafka.åpenStatistikkSakTopic"] = "aapen-k9statistikk-sak-v2"
        map["nav.nokkeltall.enheter"] = "NAV DRIFT"
        return map.toMap()
    }

    private fun String.getAsJson(): JSONObject {
        val httpClient = HttpClient()
        return runBlocking {
            val response = httpClient.get(this@getAsJson)
            JSONObject(response.bodyAsText())
        }
    }
}
