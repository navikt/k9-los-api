package no.nav.k9.integrasjon.kafka2

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

private const val ID_PREFIX = "srvpps-k9los-"

internal class KafkaConsumerConfig {

    private val bootstrapServers = ""
    val username = ""
    private val password = ""
    private val trustStorePath = ""
    private val trustStorePassword = ""

    fun consumerConfigs(clientId: String): Map<String, Any?> {
        val props: MutableMap<String, Any> = HashMap()
        props[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[CommonClientConfigs.CLIENT_ID_CONFIG] = ID_PREFIX + clientId
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java

        setSecurity(username, props)
        setUsernameAndPassword(username, password, props)

        return props
    }

    private fun setSecurity(username: String?, properties: MutableMap<String, Any>) {
        if (username != null && username.isNotEmpty()) {
            properties["security.protocol"] = "SASL_SSL"
            properties["sasl.mechanism"] = "PLAIN"
            properties[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = trustStorePath
            properties[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = trustStorePassword
        }
    }

    private fun setUsernameAndPassword(username: String?, password: String?, properties: MutableMap<String, Any>) {
        if (username != null && username.isNotEmpty()
            && password != null && password.isNotEmpty()
        ) {
            val jaasTemplate =
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";"
            val jaasCfg = String.format(jaasTemplate, username, password)
            properties["sasl.jaas.config"] = jaasCfg
        }
    }
}
