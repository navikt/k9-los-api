package no.nav.k9.aksjonspunktbehandling.k9sak

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.k9.Configuration
import no.nav.k9.aksjonspunktbehandling.K9sakEventHandlerV2
import no.nav.k9.aksjonspunktbehandling.SerDes
import no.nav.k9.aksjonspunktbehandling.Topic
import no.nav.k9.aksjonspunktbehandling.k9sak.kontrakt.ProduksjonsstyringHendelseKontrakt
import no.nav.k9.integrasjon.kafka.*
import no.nav.k9.integrasjon.kafka.ManagedKafkaStreams
import no.nav.k9.integrasjon.kafka.ManagedStreamHealthy
import no.nav.k9.integrasjon.kafka.ManagedStreamReady
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory

internal class K9SakStream constructor(
    kafkaConfig: IKafkaConfig,
    configuration: Configuration,
    k9sakEventHandlerv2: K9sakEventHandlerV2
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(
            configuration = configuration,
            k9sakEventHandler = k9sakEventHandlerv2
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "K9SakHendelseV1"

        private val log = LoggerFactory.getLogger(K9SakStream::class.java)

        private fun topology(
            configuration: Configuration,
            k9sakEventHandler: K9sakEventHandlerV2
        ): Topology {
            val builder = StreamsBuilder()
            val fromTopic = Topic(
                name = configuration.getK9SakTopic(),
                serDes = K9SakEventSerDes()
            )
            builder
                .stream(
                    fromTopic.name,
                    Consumed.with(fromTopic.keySerde, fromTopic.valueSerde)
                ).peek { _, e -> log.info("--> K9SakHendelse: ${e.tryggToString() }") }
                .foreach { _, entry ->
                    if (entry != null) {
                        runBlocking {
                            k9sakEventHandler.prosesser(entry)
                        }
                    }
                }
            return builder.build()
        }

        class K9SakEventSerDes : SerDes<ProduksjonsstyringHendelseKontrakt>() {
            override fun deserialize(topic: String?, data: ByteArray?): ProduksjonsstyringHendelseKontrakt? {
                return data?.let {
                    return try {
                        objectMapper.readValue(it)
                    } catch (e: Exception) {
                        log.warn("", e)
                        log.warn(String(it))
                        throw e
                    }
                }
            }
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
