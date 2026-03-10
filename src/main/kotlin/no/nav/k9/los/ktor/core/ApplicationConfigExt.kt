package no.nav.k9.los.ktor.core

import io.ktor.server.config.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("no.nav.k9.los.ktor.core.ApplicationConfigExt")

private fun ApplicationConfig.kildeOgVerdiOrNull(key: String) : Pair<String, String>? {
    val fromApplicationConfig = propertyOrNull(key)?.getString()
    if (!fromApplicationConfig.isNullOrBlank()) return "ApplicationConfig" to fromApplicationConfig

    val fromEnv = System.getenv(key)
    if (!fromEnv.isNullOrBlank()) return "EnvironmentVariable" to fromEnv

    val fromProperty = System.getProperty(key)
    if (!fromProperty.isNullOrBlank()) return "SystemProperty" to fromProperty

    return null
}

private fun ApplicationConfig.getString(key: String, secret: Boolean, optional: Boolean) : String? {
    val (kilde, verdi) = kildeOgVerdiOrNull(key) ?: return when (optional) {
        true -> null
        false -> throw IllegalArgumentException("$key må settes.")
    }

    logger.info("{}={} ($kilde)", key, if (secret) "***" else verdi)

    return verdi
}

fun ApplicationConfig.getRequiredString(key: String, secret: Boolean) : String = getString(key, secret, false)!!

fun ApplicationConfig.getOptionalString(key: String, secret: Boolean) : String? = getString(key, secret, true)

fun ApplicationConfig.id() : String = getRequiredString("ktor.application.id", secret = false)
