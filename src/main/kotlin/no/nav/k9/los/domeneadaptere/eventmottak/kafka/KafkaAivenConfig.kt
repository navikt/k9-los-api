package no.nav.k9.los.domeneadaptere.eventmottak.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.*

private val logger: Logger = LoggerFactory.getLogger(KafkaAivenConfig::class.java)
private const val ID_PREFIX = "srvpps-k9los-"

class KafkaAivenConfig(
    private val applicationId: String,
    bootstrapServers: String,
    trustStore: Pair<String, String>,
    keyStore: Pair<String, String>,
    credStorePassword: String,
    override val unreadyAfterConsumerStoppedIn: Duration,
    private val defaultOffsetResetStrategy: OffsetResetStrategy,
): IKafkaConfig {
    private val fellesProperties = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, credStorePassword)

        //put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        medTrustStore(trustStore)
        medKeyStore(keyStore)
    }

    override fun producer(name: String) = Properties().apply {
        putAll(fellesProperties)
        put(ProducerConfig.CLIENT_ID_CONFIG, "$ID_PREFIX$name")
        put(ProducerConfig.LINGER_MS_CONFIG, 100)
        put(ProducerConfig.BATCH_SIZE_CONFIG, 128 * 1024) // 128 KB
    }

    override fun consumer(name: String, offsetResetStrategy: OffsetResetStrategy?) = Properties().apply {
        putAll(fellesProperties)
        // Group id må være lik den tidligere application.id for å beholde committede offsets
        put(ConsumerConfig.GROUP_ID_CONFIG, applicationId + name)
        put(ConsumerConfig.CLIENT_ID_CONFIG, "$ID_PREFIX$name")
        put(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            (offsetResetStrategy ?: defaultOffsetResetStrategy).toString().lowercase()
        )
        // Vi committer selv, én gang per partisjon per poll-runde, etter at meldingene er behandlet
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100)
    }
}

private fun Properties.medTrustStore(trustStore: Pair<String, String>) {
    try {
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, File(trustStore.first).absolutePath)
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, trustStore.second)
        put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "JKS")
        logger.info("Truststore på '${trustStore.first}' konfigurert.")
    } catch (cause: Throwable) {
        logger.error(
            "Feilet for konfigurering av truststore på '${trustStore.first}'",
            cause
        )
    }
}

private fun Properties.medKeyStore(keyStore: Pair<String, String>) {
    try {
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, File(keyStore.first).absolutePath)
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keyStore.second)
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
        logger.info("Keystore på '${keyStore.first}' konfigurert.")
    } catch (cause: Throwable) {
        logger.error(
            "Feilet for konfigurering av keystore på '${keyStore.first}'",
            cause
        )
    }
}