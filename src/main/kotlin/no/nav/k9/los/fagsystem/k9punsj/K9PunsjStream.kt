package no.nav.k9.los.fagsystem.k9punsj

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.Configuration
import no.nav.k9.los.aksjonspunktbehandling.SerDes
import no.nav.k9.los.aksjonspunktbehandling.Topic
import no.nav.k9.los.fagsystem.k9punsj.kontrakt.ProduksjonsstyringHendelse
import no.nav.k9.los.fagsystem.k9sak.K9SakStream
import no.nav.k9.los.integrasjon.kafka.ManagedKafkaStreams
import no.nav.k9.los.integrasjon.kafka.ManagedStreamHealthy
import no.nav.k9.los.integrasjon.kafka.ManagedStreamReady
import no.nav.k9.los.integrasjon.kafka.IKafkaConfig
import no.nav.k9.los.utils.LosObjectMapper
import no.nav.k9.los.utils.TransientFeilHåndterer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory

internal class K9PunsjStream constructor(
    kafkaConfig: IKafkaConfig,
    configuration: Configuration,
    k9punsjEventHandlerv2: K9PunsjEventHandlerV2
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME, OffsetResetStrategy.NONE),
        topology = topology(
            configuration = configuration,
            k9PunsjEventHandler = k9punsjEventHandlerv2
        ),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "K9PunsjHendelseV1"

        private val log = LoggerFactory.getLogger(K9SakStream::class.java)

        private fun topology(
            configuration: Configuration,
            k9PunsjEventHandler: K9PunsjEventHandlerV2
        ): Topology {
            val builder = StreamsBuilder()
            val fromTopic = Topic(
                name = configuration.getK9PunsjTopic(),
                serDes = K9PunsjEventSerDes()
            )
            builder
                .stream(
                    fromTopic.name,
                    Consumed.with(fromTopic.keySerde, fromTopic.valueSerde)
                ).peek { _, e -> log.info("--> K9PunsjHendelse: ${e.safeToString() }") }
                .foreach { _, entry ->
                    if (entry != null) {
                        TransientFeilHåndterer().utfør(NAME) {
                            runBlocking {
                             k9PunsjEventHandler.prosesser(entry) }
                        }
                    }
                }
            return builder.build()
        }

        class K9PunsjEventSerDes : SerDes<ProduksjonsstyringHendelse>() {
            override fun deserialize(topic: String?, data: ByteArray?): ProduksjonsstyringHendelse? {
                return data?.let {
                    return try {
                        LosObjectMapper.instance.readValue(it)
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
