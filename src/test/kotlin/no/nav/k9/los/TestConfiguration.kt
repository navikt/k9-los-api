package no.nav.k9.los

import no.nav.helse.dusseldorf.testsupport.jws.ClientCredentials

object TestConfiguration {
    private val config = mapOf(
        "ktor.deployment.port" to "8020",
        "ktor.deployment.host" to "127.0.0.1",

        "nav.register_urls.k9_url" to "http://localhost:8080",
        "nav.register_urls.k9_klage_url" to "http://localhost:8080",
        "nav.register_urls.k9_frontend_url" to "http://localhost:9000",
        "nav.register_urls.k9_punsj_frontend_url" to "http://localhost:8080",
        "nav.register_urls.aktivitetspenger_frontend_url" to "http://localhost:9005",
        "nav.register_urls.sif_abac_pdp_url" to "http://localhost:8913",

        "nav.auth.clients.size" to "1",
        "nav.auth.clients.0.alias" to "azure-v2",
        "nav.auth.clients.0.client_id" to "k9-los",
        "nav.auth.clients.0.private_key_jwk" to ClientCredentials.ClientA.privateKeyJwk,
        "nav.auth.clients.0.certificate_hex_thumbprint" to ClientCredentials.ClientA.certificateHexThumbprint,
        "nav.auth.clients.0.discovery_endpoint" to "http://azure-mock:8100/v2.0/.well-known/openid-configuration",

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
