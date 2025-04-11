package no.nav.k9.los

import io.ktor.server.config.*
import no.nav.helse.dusseldorf.ktor.auth.clients
import no.nav.helse.dusseldorf.ktor.auth.issuers
import no.nav.helse.dusseldorf.ktor.auth.withoutAdditionalClaimRules
import no.nav.helse.dusseldorf.ktor.core.getOptionalString
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.KafkaAivenConfig
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.KafkaConfig
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.createHikariConfig
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import java.net.URI
import java.time.Duration
import java.time.temporal.ChronoUnit

data class Configuration(private val config: ApplicationConfig) {
    companion object {
        internal const val AZURE_V2_ALIAS = "azure-v2"
    }

    private val clients = config.clients()

    internal fun issuers() = config.issuers().withoutAdditionalClaimRules()

    internal fun clients() = clients

    internal fun pdlUrl() = URI(config.getRequiredString("nav.register_urls.pdl_url", secret = false))
    internal fun k9Url() = config.getRequiredString("nav.register_urls.k9_url", secret = false)
    internal fun k9KlageUrl() = config.getRequiredString("nav.register_urls.k9_klage_url", secret = false)
    internal fun k9FrontendUrl() = config.getRequiredString("nav.register_urls.k9_frontend_url", secret = false)
    internal fun k9PunsjFrontendUrl() =
        config.getRequiredString("nav.register_urls.k9_punsj_frontend_url", secret = false)
    internal fun sifAbacPdpUrl() = config.getRequiredString("nav.register_urls.sif_abac_pdp", secret = false)

    internal val abacUsername = config.getRequiredString("nav.abac.system_user", secret = false)
    internal val abacPassword = config.getRequiredString("nav.abac.system_user_password", secret = false)
    internal val abacEndpointUrl = config.getRequiredString("nav.abac.url", secret = false)

    internal fun hikariConfig() = createHikariConfig(
        jdbcUrl = config.getRequiredString("nav.db.url", secret = false),
        username = config.getOptionalString("nav.db.username", secret = false),
        password = config.getOptionalString("nav.db.password", secret = true)
    )

    internal fun getAksjonspunkthendelseTopic(): String {
        if (k9SakConsumerAiven()) {
            return config.getOptionalString("nav.kafka.aksjonshendelseTopic", secret = false)
                ?: "k9saksbehandling.k9sak-aksjonspunkthendelse"
        } else {
            return "privat-k9-aksjonspunkthendelse"
        }
    }

    internal fun getKlageOppgavemeldingerTopic(): String {
        return config.getOptionalString("nav.kafka.klageTilLosTopic", secret = false)
            ?: "k9saksbehandling.oppgavemeldinger-klage-til-los"
    }

    internal fun getK9SakTopic(): String {
        return config.getOptionalString("nav.kafka_aiven.k9sakTopic", secret = false)
            ?: "k9saksbehandling.privat-k9-produksjonsstyring-sak-v1"
    }

    internal fun getK9PunsjTopic(): String {
        return config.getOptionalString("nav.kafka_aiven.k9punsjTopic", secret = false)
            ?: "k9saksbehandling.privat-k9-produksjonsstyring-punsj-v1"
    }

    internal fun getAksjonspunkthendelsePunsjTopic(): String {
        if (punsjConsumerAiven()) {
            return config.getOptionalString("nav.kafka.punsjAksjonshendelseTopic", secret = false)
                ?: "k9saksbehandling.punsj-aksjonspunkthendelse-v1"
        } else {
            return "privat-k9punsj-aksjonspunkthendelse-v1"
        }
    }

    internal fun getAksjonspunkthendelseTilbakeTopic(): String {
        if (tilbakeConsumerAiven()) {
            return config.getOptionalString("nav.kafka.tilbakekrevingaksjonshendelseTopic", secret = false)
                ?: "k9saksbehandling.tilbakekreving-hendelse-los"
        } else {
            return "privat-tilbakekreving-k9loshendelse-v1"
        }
    }

    internal fun getSakOgBehandlingTopic(): String {
        return config.getOptionalString("nav.kafka.sakOgBehandlingTopic", secret = false)
            ?: ""
    }

    internal fun getÅpenStatistikkSakTopic(): String {
        return config.getOptionalString("nav.kafka.åpenStatistikkSakTopic", secret = false)
            ?: ""
    }

    internal fun getÅpenStatistikkBehandlingTopic(): String {
        return config.getOptionalString("nav.kafka.åpenStatistikkBehandlingTopic", secret = false)
            ?: ""
    }

    internal fun nyOppgavestyringAktivert(): Boolean {
        return config.getOptionalString("nav.features.nyOppgavestyring", secret = false).toBoolean()
    }

    internal fun nyOppgavestyringDvhSendingAktivert(): Boolean {
        return config.getOptionalString("nav.features.nyOppgavestyringDvhSending", secret = false).toBoolean()
    }

    internal fun valgtPdp(): ValgtPdp {
        return when (config.getOptionalString("nav.features.valgtPdp", secret = false)) {
            "abac-k9" -> ValgtPdp.ABAC_K9
            "sif-abac-pdp" -> ValgtPdp.SIF_ABAC_PDP
            "begge" -> ValgtPdp.BEGGE
            else -> ValgtPdp.ABAC_K9
        }
    }

    enum class ValgtPdp {
        ABAC_K9,
        SIF_ABAC_PDP,
        BEGGE
    }

    internal fun nyOppgavestyringRestAktivert(): Boolean {
        return config.getOptionalString("nav.features.nyOppgavestyringRestApi", secret = false).toBoolean()
    }

    internal fun punsjConsumerAiven(): Boolean {
        return config.getOptionalString("nav.features.punsjConsumerAiven", secret = false).toBoolean()
    }

    internal fun tilbakeConsumerAiven(): Boolean {
        return config.getOptionalString("nav.features.tilbakeConsumerAiven", secret = false).toBoolean()
    }

    internal fun k9SakConsumerAiven(): Boolean {
        return config.getOptionalString("nav.features.k9SakConsumerAiven", secret = false).toBoolean()
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

    internal fun getProfileAwareKafkaAivenConfig() =
        // Bytter ut aivenkonfig med onprem kafkakonfig som er støttet i vtp.
        if (koinProfile == KoinProfile.LOCAL) getKafkaConfig() else getKafkaAivenConfig()


    internal fun getKafkaAivenConfig(defaultOffsetResetStrategy: OffsetResetStrategy = OffsetResetStrategy.NONE): KafkaAivenConfig {
        val bootstrapServers = config.getRequiredString("nav.kafka_aiven.bootstrap_servers", secret = false)
        val trustStorePath = config.getRequiredString("nav.kafka_aiven.trust_store_path", secret = false)
        val keyStorePath = config.getRequiredString("nav.kafka_aiven.key_store_path", secret = false)
        val credStorePassword = config.getRequiredString("nav.kafka_aiven.credstore_password", secret = true)

        return KafkaAivenConfig(
            applicationId = config.getRequiredString("nav.kafka_aiven.application_id", false),
            bootstrapServers = bootstrapServers,
            trustStore = Pair(trustStorePath, credStorePassword),
            keyStore = Pair(keyStorePath, credStorePassword),
            credStorePassword = credStorePassword,
            exactlyOnce = false,
            defaultOffsetResetStrategy = defaultOffsetResetStrategy,
            unreadyAfterStreamStoppedIn = unreadyAfterStreamStoppedIn()
        )
    }

    private fun unreadyAfterStreamStoppedIn() = Duration.of(
        config.getRequiredString("nav.kafka.unready_after_stream_stopped_in.amount", secret = false).toLong(),
        ChronoUnit.valueOf(config.getRequiredString("nav.kafka.unready_after_stream_stopped_in.unit", secret = false))
    )

    fun getVaultDbPath(): String {
        return config.getOptionalString("nav.db.vault_mountpath", secret = false)!!
    }

    fun databaseName(): String {
        return "k9-los"
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

    fun enheter(): List<String> {
        return config.getOptionalString("nav.nokkeltall.enheter", secret = false)?.split(",") ?: emptyList()
    }
}
