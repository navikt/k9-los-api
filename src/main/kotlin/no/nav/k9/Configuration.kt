package no.nav.k9

import io.ktor.config.*
import io.ktor.util.*
import no.nav.helse.dusseldorf.ktor.auth.clients
import no.nav.helse.dusseldorf.ktor.auth.issuers
import no.nav.helse.dusseldorf.ktor.auth.withoutAdditionalClaimRules
import no.nav.helse.dusseldorf.ktor.core.getOptionalString
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import no.nav.k9.db.createHikariConfig
import no.nav.k9.integrasjon.kafka.KafkaConfig
import java.net.URI
import java.time.Duration
import java.time.temporal.ChronoUnit

@KtorExperimentalAPI
data class Configuration(private val config: ApplicationConfig) {
    companion object {
        internal const val NAIS_STS_ALIAS = "nais-sts"
        internal const val AZURE_V2_ALIAS = "azure-v2"
    }

    private val clients = config.clients()

    internal fun issuers() = config.issuers().withoutAdditionalClaimRules()

    internal fun clients() = clients

    internal fun pdlUrl() = URI(config.getRequiredString("nav.register_urls.pdl_url", secret = false))
    internal fun k9Url() = config.getRequiredString("nav.register_urls.k9_url", secret = false)
    internal fun omsorgspengerUrl() = config.getRequiredString("nav.register_urls.omsorgspenger_url", secret = false)

    internal fun omsorgspenger_sak_scope() = config.getRequiredString("nav.scopes.omsorgspenger_sak", secret = false)

    internal val abacUsername = config.getRequiredString("nav.abac.system_user", secret = false)
    internal val abacPassword = config.getRequiredString("nav.abac.system_user_password", secret = false)
    internal val abacEndpointUrl = config.getRequiredString("nav.abac.url", secret = false)

    internal fun hikariConfig() = createHikariConfig(
        jdbcUrl = config.getRequiredString("nav.db.url", secret = false),
        username = config.getOptionalString("nav.db.username", secret = false),
        password = config.getOptionalString("nav.db.password", secret = true)
    )

    internal fun getAksjonspunkthendelseTopic(): String {
        return config.getOptionalString("nav.kafka.aksjonshendelseTopic", secret = false)
            ?: "privat-k9-aksjonspunkthendelse"
    }

    internal fun getAksjonspunkthendelsePunsjTopic(): String {
        return config.getOptionalString("nav.kafka.punsjAksjonshendelseTopic", secret = false)
            ?: "privat-k9punsj-aksjonspunkthendelse-v1"
    }

    internal fun getAksjonspunkthendelseTilbakeTopic(): String {
        return config.getOptionalString("nav.kafka.tilbakekrevingaksjonshendelseTopic", secret = false)
            ?: "privat-tilbakekreving-k9loshendelse-v1"
    }

    internal fun getSakOgBehandlingTopic(): String {
        return config.getOptionalString("nav.kafka.sakOgBehandlingTopic", secret = false)
            ?: ""
    }

    internal fun getStatistikkSakTopic(): String {
        return config.getOptionalString("nav.kafka.statistikkSakTopic", secret = false)
            ?: ""
    }

    internal fun getStatistikkBehandlingTopic(): String {
        return config.getOptionalString("nav.kafka.statistikkBehandlingTopic", secret = false)
            ?: ""
    }

    internal fun getKafkaConfig() =
        config.getRequiredString("nav.kafka.bootstrap_servers", secret = false).let { bootstrapServers ->
            val trustStore = config.getRequiredString("nav.trust_store.path", secret = false).let { trustStorePath ->
                config.getOptionalString("nav.trust_store.password", secret = true)?.let { trustStorePassword ->
                    Pair(trustStorePath, trustStorePassword)
                }
            }

            KafkaConfig(
                bootstrapServers = bootstrapServers,
                credentials = Pair(
                    config.getRequiredString("nav.kafka.username", secret = false),
                    config.getRequiredString("nav.kafka.password", secret = true)
                ),
                trustStore = trustStore,
                exactlyOnce = false,
                unreadyAfterStreamStoppedIn = unreadyAfterStreamStoppedIn()
            )
        }

    internal fun getCookieName(): String {
        return config.getRequiredString("nav.auth.cookie_name", secret = false)
    }

    private fun unreadyAfterStreamStoppedIn() = Duration.of(
        config.getRequiredString("nav.kafka.unready_after_stream_stopped_in.amount", secret = false).toLong(),
        ChronoUnit.valueOf(config.getRequiredString("nav.kafka.unready_after_stream_stopped_in.unit", secret = false))
    )

    fun getOppgaveBaseUri(): URI {
        return URI(config.getRequiredString("nav.gosys.baseuri", secret = false))
    }

    fun getVaultDbPath(): String {
        return config.getOptionalString("nav.db.vault_mountpath", secret = false)!!
    }

    fun databaseName(): String {
        return "k9-los"
    }

    fun azureClientId(): String {
        return config.getOptionalString("nav.auth.azure_client_id", secret = false)!!
    }

    fun azureClientSecret(): String {
        return config.getOptionalString("nav.auth.azure_client_secret", secret = true)!!
    }

    fun auditEnabled(): Boolean {
        return config.getRequiredString("nav.audit.enabled", secret = false).toBoolean()
    }

    fun auditVendor(): String {
        return config.getRequiredString("nav.audit.vendor", secret = false)
    }

    fun auditProduct(): String {
        return config.getRequiredString("nav.audit.product", secret = false)
    }

    var koinProfile = KoinProfile.LOCAL

    init {
        val clustername = config.getOptionalString("nav.clustername", secret = false)
        if (config.getOptionalString("nav.db.vault_mountpath", secret = false).isNullOrBlank()) {
            koinProfile = KoinProfile.LOCAL
        } else if (if (clustername.isNullOrBlank()) {
                false
            } else clustername == "dev-fss"
        ) {
            koinProfile = KoinProfile.PREPROD
        } else if (if (clustername.isNullOrBlank()) {
                false
            } else clustername == "prod-fss"
        ) {
            koinProfile = KoinProfile.PROD
        }
    }

    fun koinProfile(): KoinProfile {
        return koinProfile
    }

}
