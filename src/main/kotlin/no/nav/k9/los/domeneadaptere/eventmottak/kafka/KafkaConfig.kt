package no.nav.k9.los.domeneadaptere.eventmottak.kafka

import org.apache.kafka.clients.consumer.OffsetResetStrategy
import java.time.Duration
import java.util.*


interface IKafkaConfig {
    val unreadyAfterConsumerStoppedIn: Duration

    /** @param name blir suffiks i consumer group id. Endring av navn medfører at offsets nullstilles. */
    fun consumer(name: String, offsetResetStrategy: OffsetResetStrategy? = null): Properties
    fun producer(name: String): Properties
}
