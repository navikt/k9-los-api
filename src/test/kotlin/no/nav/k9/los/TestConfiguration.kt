package no.nav.k9.los

import no.nav.k9.los.testsupport.jws.ClientCredentials

object TestConfiguration {

    fun asMap(
        port: Int = 8020,
    ): Map<String, String> {
        val map = mutableMapOf<String, String>()

        map["ktor.deployment.port"] = "$port"

        map["nav.register_urls.k9_url"] = "http://localhost:8080"
        map["nav.register_urls.k9_klage_url"] = "http://localhost:8080"
        map["nav.register_urls.k9_frontend_url"] = "http://localhost:9000"
        map["nav.register_urls.k9_punsj_frontend_url"] = "http://localhost:8080"
        map["nav.register_urls.sif_abac_pdp_url"] = "http://localhost:8913"
        map["nav.auth.trustore.path"] = "vtp"
        map["nav.auth.trustore.password"] = "vtp"

        map["nav.auth.clients.size"] = "1"
        map["nav.auth.clients.0.alias"] = "azure-v2"
        map["nav.auth.clients.0.client_id"] = "k9-los"
        map["nav.auth.clients.0.private_key_jwk"] = ClientCredentials.ClientA.privateKeyJwk
        map["nav.auth.clients.0.certificate_hex_thumbprint"] = ClientCredentials.ClientA.certificateHexThumbprint
        map["nav.auth.clients.0.discovery_endpoint"] = "http://azure-mock:8100/v2.0/.well-known/openid-configuration"

        map["nav.kafka_aiven.bootstrap_servers"] = "vtp:9093"
        map["nav.kafka.unready_after_stream_stopped_in.amount"] = "1010"
        map["nav.kafka.unready_after_stream_stopped_in.unit"] = "SECONDS"
        map["nav.kafka_aiven.key_store_path"] = "${System.getProperty("user.home")}/.modig/keystore.jks"
        map["nav.kafka_aiven.trust_store_path"] = "${System.getProperty("user.home")}/.modig/truststore.jks"
        map["nav.kafka_aiven.credstore_password"] = "vtpvtp"
        map["nav.kafka_aiven.application_id"] = "k9-los-api"

        map["nav.kafka.statistikkSakTopic"] = "privat-k9statistikk-sak-v1"

        map["nav.db.url"] = "jdbc:postgresql://localhost:5432/k9los_unit"
        map["nav.db.username"] = "k9los_unit"
        map["nav.db.password"] = "k9los_unit"
        map["nav.features.nyOppgavestyring"] = "true"
        map["nav.features.nyOppgavestyringRestApi"] = "true"
        map["nav.kafka.åpenStatistikkBehandlingTopic"] = "aapen-k9statistikk-behandling-v2"
        map["nav.kafka.åpenStatistikkSakTopic"] = "aapen-k9statistikk-sak-v2"
        map["nav.nokkeltall.enheter"] = "NAV DRIFT"
        return map.toMap()
    }
}
