package no.nav.k9.los

import no.nav.helse.dusseldorf.testsupport.jws.ClientCredentials

object TestConfiguration {
    private val home = System.getProperty("user.home")

    private val config = mapOf(
        "ktor.deployment.port" to "8020",
        "ktor.deployment.host" to "127.0.0.1",

        "nav.register_urls.k9_url" to "http://localhost:8080",
        "nav.register_urls.k9_klage_url" to "http://localhost:8080",
        "nav.register_urls.k9_frontend_url" to "http://localhost:9000",
        "nav.register_urls.k9_punsj_frontend_url" to "http://localhost:8080",
        "nav.register_urls.aktivitetspenger_frontend_url" to "http://localhost:9005",
        "nav.register_urls.sif_abac_pdp_url" to "http://localhost:8913",

        "nav.auth.trustore.path" to "vtp",
        "nav.auth.trustore.password" to "vtp",
        "nav.auth.clients.size" to "1",
        "nav.auth.clients.0.alias" to "azure-v2",
        "nav.auth.clients.0.client_id" to "k9-los",
        "nav.auth.clients.0.private_key_jwk" to ClientCredentials.ClientA.privateKeyJwk,
        "nav.auth.clients.0.certificate_hex_thumbprint" to ClientCredentials.ClientA.certificateHexThumbprint,
        "nav.auth.clients.0.discovery_endpoint" to "http://azure-mock:8100/v2.0/.well-known/openid-configuration",

        "nav.kafka_aiven.bootstrap_servers" to "vtp:9093",
        "nav.kafka_aiven.key_store_path" to "$home/.modig/keystore.jks",
        "nav.kafka_aiven.trust_store_path" to "$home/.modig/truststore.jks",
        "nav.kafka_aiven.credstore_password" to "vtpvtp",
        "nav.kafka_aiven.application_id" to "k9-los-api",

        "nav.kafka.unready_after_stream_stopped_in.amount" to "1010",
        "nav.kafka.unready_after_stream_stopped_in.unit" to "SECONDS",
        "nav.kafka.statistikkSakTopic" to "privat-k9statistikk-sak-v1",
        "nav.kafka.ungSakTopic" to "k9saksbehandling.ung-sak-aksjonspunkthendelse",
        "nav.kafka.ungTilbakeTopic" to "k9saksbehandling.ung-tilbakekreving-hendelse-los",
        "nav.kafka.åpenStatistikkBehandlingTopic" to "aapen-k9statistikk-behandling-v2",
        "nav.kafka.åpenStatistikkSakTopic" to "aapen-k9statistikk-sak-v2",

        "nav.db.url" to "jdbc:postgresql://localhost:5432/k9los_unit",
        "nav.db.username" to "k9los_unit",
        "nav.db.password" to "k9los_unit",
        "nav.db.migrering_etter_oppstart" to "false",

        "nav.nokkeltall.enheter" to "NAV DRIFT",
    )

    fun asArguments(): Array<String> {
        val list = config.map { (configKey, configValue) ->
            "-P:$configKey=$configValue"
        }
        return list.toTypedArray()
    }
}
