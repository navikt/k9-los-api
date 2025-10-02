package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka

import org.apache.kafka.clients.consumer.OffsetResetStrategy
import java.time.Duration
import java.util.*


interface IKafkaConfig {
    val unreadyAfterStreamStoppedIn: Duration
    fun stream(name: String, offsetResetStrategy: OffsetResetStrategy? = null): Properties
    fun producer(name: String): Properties
}
