package no.nav.k9.los.integrasjon.kafka

import org.apache.kafka.common.header.Header
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Transformer
import org.apache.kafka.streams.processor.ProcessorContext
import org.slf4j.LoggerFactory

class FilterByHeader<K, V>(
    val predicate: (Header) -> Boolean
) : Transformer<K, V, KeyValue<K, V>> {

    val logger = LoggerFactory.getLogger(FilterByHeader::class.java)

    lateinit var context: ProcessorContext

    override fun init(context: ProcessorContext) {
        this.context = context
    }

    override fun close() {}

    override fun transform(key: K, value: V): KeyValue<K, V>? {
        val headers = context.headers()
        if (headers.any { header -> predicate(header) }) {
            logger.info("Heartbeat filtrert bort")
            return null
        }
        return KeyValue(key, value)
    }
}

fun <K, V> KStream<K, V>.filterNotHeartbeats(): KStream<K, V> {
    val filterByHeader = FilterByHeader<K, V> { header: Header -> header.key() != "Heartbeat" }
    transform({ filterByHeader })
    return this
}
