package no.nav.k9.los.fagsystem.k9sak

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.SerDes
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.Topic
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.IKafkaConfig
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedKafkaStreams
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedStreamHealthy
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.kafka.ManagedStreamReady
import no.nav.k9.los.utils.LosObjectMapper
import no.nav.k9.los.utils.TransientFeilHåndterer
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.ProduksjonsstyringHendelse
import org.apache.kafka.clients.consumer.OffsetResetStrategy
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
        properties = kafkaConfig.stream(NAME, OffsetResetStrategy.EARLIEST),
        topology = topology(
            configuration = configuration,
            k9sakEventHandler = k9sakEventHandlerv2
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "K9SakProduksjonsstyringHendelse"

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
                ).peek { _, e -> log.info("--> K9SakHendelse: ${e.tryggToString()}") }
                .foreach { _, entry ->
                    if (entry != null) {
                        TransientFeilHåndterer().utfør(NAME) {
                            runBlocking {
                                k9sakEventHandler.prosesser(entry)
                            }
                        }
                    }
                }
            return builder.build()
        }

        class K9SakEventSerDes : SerDes<ProduksjonsstyringHendelse>() {
            override fun deserialize(topic: String?, data: ByteArray?): ProduksjonsstyringHendelse? {
                return data?.let {
                    return try {
                        LosObjectMapper.instance.readValue(it)
                    }
                    catch (e: Exception) {
                        log.warn("", e)
                        log.warn(String(it))
                        null
                    }
                }
            }
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
