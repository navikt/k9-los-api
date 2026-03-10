package no.nav.k9.los.ktor.auth

import io.ktor.server.config.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import no.nav.k9.los.ktor.core.getOptionalString
import no.nav.k9.los.ktor.core.getRequiredString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL

private const val AZURE_TYPE = "azure"
private const val ISSUER = "issuer"
private const val JWKS_URI = "jwks_uri"
private const val TOKEN_ENDPOINT = "token_endpoint"

private val logger: Logger = LoggerFactory.getLogger("no.nav.k9.los.ktor.auth.AuthConfig")

private fun ApplicationConfig.getOptionalCsvList(key: String): List<String> {
    val csv = getOptionalString(key, secret = false) ?: return emptyList()
    return csv.replace(" ", "").split(",")
}

fun ApplicationConfig.issuers(path: String = "nav.auth.issuers") : Map<String, Issuer> {
    val issuersConfigList = configList(path)
        .asReversed().associateBy { it.getRequiredString("alias", false) }
    val issuers = mutableMapOf<String, Issuer>()
    for ((alias, issuerConfig) in issuersConfigList) {
        logger.info("Issuer[$alias]")
        val discoveryJson = runBlocking { issuerConfig.getOptionalString("discovery_endpoint", false)?.discover(listOf(ISSUER, JWKS_URI)) }
        val issuer = if (discoveryJson != null) (discoveryJson[ISSUER] as JsonPrimitive).content else issuerConfig.getOptionalString(ISSUER, false)
        val jwksUrl = if (discoveryJson != null) (discoveryJson[JWKS_URI] as JsonPrimitive).content else issuerConfig.getOptionalString(JWKS_URI, false)
        logger.info("Issuer[$alias].issuer = '$issuer'")
        logger.info("Issuer[$alias].jwks_uri = '$jwksUrl'")
        if (issuer == null || jwksUrl == null) {
            logger.info("Issuer[$alias] ikke konfigurert.")
            continue
        } else {
            logger.info("Issuer[$alias] er konfigurert.")
        }
        val type = issuerConfig.getOptionalString("type", false)
        val audience = issuerConfig.getOptionalString("audience", false)
        val resolvedIssuer = if (AZURE_TYPE.equals(type, false)) {
            checkNotNull(audience) { "'audience' må settes for en issuer med type='azure'" }
            val authorizedClient = issuerConfig.getOptionalCsvList("azure.authorized_clients").toSet()
            val requiredGroups = issuerConfig.getOptionalCsvList("azure.required_groups").toSet()
            val requiredRoles = issuerConfig.getOptionalCsvList("azure.required_roles").toSet()
            val requiredScopes = issuerConfig.getOptionalCsvList("azure.required_scopes").toSet()
            val requireCertificateClientAuthentication = issuerConfig.getOptionalString("azure.require_certificate_client_authentication", false)
            Azure(
                    issuer = issuer,
                    jwksUri = URI(jwksUrl),
                    audience = audience,
                    alias = alias,
                    authorizedClients = authorizedClient,
                    requiredGroups = requiredGroups,
                    requiredRoles = requiredRoles,
                    requiredScopes = requiredScopes,
                    requireCertificateClientAuthentication = requireCertificateClientAuthentication != null && "true".equals(requireCertificateClientAuthentication, true)
            )
        } else {
            Issuer(
                    issuer = issuer,
                    jwksUri = URI(jwksUrl),
                    audience = audience,
                    alias = alias
            )
        }
        issuers[alias] = resolvedIssuer
    }
    logger.info("${issuers.size} Issuers konfigueret.")
    return issuers.toMap()
}

fun ApplicationConfig.clients(path: String = "nav.auth.clients") : Map<String, Client> {
    val clientsConfigList = configList(path)
        .asReversed().associateBy { it.getRequiredString("alias", false) }

    val clients = mutableMapOf<String, Client>()
    for ((alias, clientConfig) in clientsConfigList) {
        logger.info("Client[$alias]")
        val clientId = clientConfig.getOptionalString("client_id", false)
        if (clientId == null) {
            logger.info("Client[$alias] ikke konfigurert.")
            continue
        } else {
            logger.info("Client[$alias] er konfigurert.")
        }

        val discoveryJson = runBlocking { clientConfig.getOptionalString("discovery_endpoint", false)?.discover(listOf(TOKEN_ENDPOINT)) }
        val tokenEndpoint = URI(if (discoveryJson != null) (discoveryJson[TOKEN_ENDPOINT] as JsonPrimitive).content else clientConfig.getRequiredString(TOKEN_ENDPOINT, false))
        logger.info("Client[$alias].token_endpoint = '$tokenEndpoint'")

        val clientSecret = clientConfig.getOptionalString("client_secret", true)
        val privateKeyJwk = clientConfig.getOptionalString("private_key_jwk", true)
        check(!(clientSecret != null && privateKeyJwk != null)) { "Både 'private_key_jwk' og 'client_secret' satt for Client[$alias]. Kun en av disse kan settes per client." }
        check(!(clientSecret == null && privateKeyJwk == null)) { "Hverken 'private_key_jwk' eller 'client_secret' satt for Client[$alias]. En av disse må settes per client." }

        val resolvedClient = if (clientSecret != null) {
            ClientSecretClient(clientId, tokenEndpoint, clientSecret)
        } else {
            val certificateHexThumbprint = clientConfig.getOptionalString("certificate_hex_thumbprint", false)
            if (certificateHexThumbprint != null) {
                PrivateKeyClient(clientId, tokenEndpoint, privateKeyJwk!!, certificateHexThumbprint)
            } else {
                PrivateKeyClient(clientId, tokenEndpoint, privateKeyJwk!!)
            }
        }

        clients[alias] = resolvedClient
    }
    logger.info("${clients.size} clients konfigurert.")
    return clients.toMap()
}

private fun String.discover(requiredAttributes : List<String>) : JsonObject? {
    val asText = URL(this).readText()
    val asJson = Json.parseToJsonElement(asText).jsonObject
    return if (asJson.containsKeys(requiredAttributes)) asJson else {
        logger.warn("Response fra Discovery Endpoint inneholdt ikke attributtene '[${requiredAttributes.joinToString()}]'. Response='$asText'")
        null
    }
}

private fun JsonObject.containsKeys(requiredAttributes: List<String>): Boolean {
    requiredAttributes.forEach {
        if (!containsKey(it)) return false
    }
    return true
}

sealed interface Client {
    fun clientId(): String
    fun tokenEndpoint(): java.net.URI
}

data class ClientSecretClient(
    private val clientId: String,
    private val tokenEndpoint: java.net.URI,
    val clientSecret: String
) : Client {
    override fun clientId() = clientId
    override fun tokenEndpoint() = tokenEndpoint
}

data class PrivateKeyClient(
    private val clientId: String,
    private val tokenEndpoint: java.net.URI,
    val privateKeyJwk: String,
    val certificateHexThumbprint: String = ""
) : Client {
    override fun clientId() = clientId
    override fun tokenEndpoint() = tokenEndpoint
}
