package no.nav.k9.los.domeneadaptere.k9.eventmottak.kafka

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import no.nav.k9.los.domeneadaptere.eventmottak.kafka.ManagedKafkaConsumer
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ManagedKafkaConsumerTest {

    private val topic = "test-topic"
    private val partisjon = TopicPartition(topic, 0)

    private fun mockConsumer(vararg meldinger: String): MockConsumer<String, String> {
        val mock = MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)
        mock.schedulePollTask {
            mock.rebalance(listOf(partisjon))
            mock.updateBeginningOffsets(mapOf(partisjon to 0L))
            meldinger.forEachIndexed { offset, melding ->
                mock.addRecord(ConsumerRecord(topic, 0, offset.toLong(), "key$offset", melding))
            }
        }
        return mock
    }

    private fun consumer(
        consumerFactory: (Properties) -> Consumer<String, String>,
        håndter: (String) -> Unit
    ) = ManagedKafkaConsumer(
        name = "test",
        topic = topic,
        properties = Properties(),
        unreadyEtterStoppetI = Duration.ofMinutes(1),
        pauseEtterFeil = Duration.ofMillis(50),
        pauseFørRestart = Duration.ofMillis(50),
        consumerFactory = consumerFactory,
        håndter = håndter,
    )

    private fun committetOffset(mock: MockConsumer<String, String>) =
        mock.committed(setOf(partisjon))[partisjon]?.offset()

    @Test
    fun `committer en gang per poll-runde, etter at handleren er ferdig med meldingene`() {
        val mock = mockConsumer("melding-1", "melding-2")
        val mottatt = LinkedBlockingQueue<String>()

        val consumer = consumer({ mock }) { melding ->
            // Ingenting skal være committet mens meldingene i batchen behandles
            assertThat(committetOffset(mock)).isNull()
            mottatt.put(melding)
        }

        try {
            assertThat(mottatt.poll(10, TimeUnit.SECONDS)).isEqualTo("melding-1")
            assertThat(mottatt.poll(10, TimeUnit.SECONDS)).isEqualTo("melding-2")
            ventTil { committetOffset(mock) == 2L }
        } finally {
            consumer.stop()
        }
    }

    @Test
    fun `committer ikke og spoler tilbake nar handleren feiler`() {
        val mock = mockConsumer("melding-1")
        val forsøk = AtomicInteger()

        val consumer = consumer({ mock }) {
            forsøk.incrementAndGet()
            throw RuntimeException("Feilet med vilje")
        }

        try {
            ventTil { forsøk.get() >= 1 }
            ventTil { mock.position(partisjon) == 0L }
            assertThat(committetOffset(mock)).isNull()
        } finally {
            consumer.stop()
        }
    }

    @Test
    fun `committer meldingene for den som feiler, og spoler tilbake til den`() {
        val mock = mockConsumer("melding-1", "melding-2")
        val mottatt = LinkedBlockingQueue<String>()

        val consumer = consumer({ mock }) { melding ->
            if (melding == "melding-2") throw RuntimeException("Feilet med vilje")
            mottatt.put(melding)
        }

        try {
            assertThat(mottatt.poll(10, TimeUnit.SECONDS)).isEqualTo("melding-1")
            ventTil { committetOffset(mock) == 1L }
            ventTil { mock.position(partisjon) == 1L }
        } finally {
            consumer.stop()
        }
    }

    @Test
    fun `starter consumer-traden pa nytt nar den dor av en uventet feil`() {
        val mock = mockConsumer("melding-1")
        val antallOpprettelser = AtomicInteger()
        val mottatt = LinkedBlockingQueue<String>()

        // Første tilkobling feiler, andre fungerer
        val factory: (Properties) -> Consumer<String, String> = {
            if (antallOpprettelser.incrementAndGet() == 1) {
                throw KafkaException("Klarte ikke koble til")
            }
            mock
        }

        val consumer = consumer(factory) { mottatt.put(it) }

        try {
            assertThat(mottatt.poll(10, TimeUnit.SECONDS)).isEqualTo("melding-1")
            assertThat(antallOpprettelser.get()).isEqualTo(2)
            ventTil { committetOffset(mock) == 1L }
        } finally {
            consumer.stop()
        }
    }

    private fun ventTil(timeout: Duration = Duration.ofSeconds(10), betingelse: () -> Boolean) {
        val frist = System.currentTimeMillis() + timeout.toMillis()
        while (System.currentTimeMillis() < frist) {
            if (betingelse()) return
            Thread.sleep(10)
        }
        throw AssertionError("Betingelsen ble ikke oppfylt innen ${timeout.toSeconds()} sekunder")
    }
}

