package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.streams.StreamsConfig.*
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.*

private val logger: Logger = LoggerFactory.getLogger(KafkaConfig::class.java)
private const val ID_PREFIX = "srvpps-k9los-"

class KafkaAivenConfig(
    private val applicationId: String,
    bootstrapServers: String,
    trustStore: Pair<String, String>,
    keyStore: Pair<String, String>,
    credStorePassword: String,
    exactlyOnce: Boolean,
    override val unreadyAfterStreamStoppedIn: Duration,
    private val defaultOffsetResetStrategy: OffsetResetStrategy,
): IKafkaConfig {
    val properties = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, credStorePassword)
        medTrustStore(trustStore)
        medKeyStore(keyStore)
    }

    override fun producer(name: String) = properties.apply {
        put(ProducerConfig.CLIENT_ID_CONFIG, "$ID_PREFIX$name")
    }

    private val streams = properties.apply {
        put(DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, LogAndFailExceptionHandler::class.java)
        medProcessingGuarantee(exactlyOnce)
    }

    override fun stream(name: String, offsetResetStrategy: OffsetResetStrategy?): Properties = streams.apply {
        put(APPLICATION_ID_CONFIG, applicationId+name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, (offsetResetStrategy ?: defaultOffsetResetStrategy).toString().lowercase())
    }
}

private fun Properties.medProcessingGuarantee(exactlyOnce: Boolean) {
    if (exactlyOnce) {
        logger.info("$PROCESSING_GUARANTEE_CONFIG=$EXACTLY_ONCE")
        put(PROCESSING_GUARANTEE_CONFIG, EXACTLY_ONCE)
        put(REPLICATION_FACTOR_CONFIG, "3")
    } else {
        logger.info("$PROCESSING_GUARANTEE_CONFIG=$AT_LEAST_ONCE")
        put(PROCESSING_GUARANTEE_CONFIG, AT_LEAST_ONCE)
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