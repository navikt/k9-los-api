package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka

import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer

internal data class TopicEntry<V>(
    val metadata: Metadata,
    val data: V
)

internal data class TopicUse<V>(
    val name: String,
    val valueSerializer: Serializer<TopicEntry<V>>
) {
    internal fun keySerializer() = StringSerializer()
}

