package no.nav.k9.los.domeneadaptere.eventmottak.kafka

import io.prometheus.client.Gauge
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enkel Kafka-consumer som leser meldinger som tekst (uten deserialisering) og committer offset
 * først når handleren er ferdig med meldingen. Gir at-least-once uten Kafka Streams-maskineriet.
 *
 * Committer én gang per partisjon per poll-runde for å begrense antall rundturer mot broker.
 * Ved feil i handleren committes ingenting for meldingen som feilet (eller de etter den),
 * consumeren spoler tilbake dit og forsøker på nytt etter en kort pause.
 *
 * En consumer-tråd som dør av en uventet feil blir automatisk startet på nytt, tilsvarende
 * Kafka Streams sin REPLACE_THREAD. Consumeren rapporteres som usunn så lenge en tråd er nede.
 */
internal class ManagedKafkaConsumer(
    private val name: String,
    private val topic: String,
    private val properties: Properties,
    private val antallTråder: Int = 1,
    private val unreadyEtterStoppetI: Duration,
    private val pauseEtterFeil: Duration = Duration.ofSeconds(5),
    private val pauseFørRestart: Duration = Duration.ofSeconds(10),
    private val consumerFactory: (Properties) -> Consumer<String, String> = { props ->
        KafkaConsumer(props, StringDeserializer(), StringDeserializer())
    },
    private val håndter: (melding: String) -> Unit,
) {
    private companion object {
        private val consumerStatus = Gauge
            .build("stream_status", "Indikerer consumerens status. 0 er Running, 1 er stopped.")
            .labelNames("stream")
            .register()

        private val POLL_TIMEOUT: Duration = Duration.ofSeconds(1)
    }

    private val log = LoggerFactory.getLogger("no.nav.$name.consumer")
    private val kjører = AtomicBoolean(true)
    private val consumers = CopyOnWriteArrayList<Consumer<String, String>>()
    private val trådteller = AtomicInteger()
    private val trådetilkoblet = AtomicInteger()
    private val executor = Executors.newFixedThreadPool(antallTråder) { runnable ->
        Thread(runnable, "$name-${trådteller.incrementAndGet()}").apply { isDaemon = true }
    }

    @Volatile
    private var stoppet: LocalDateTime? = null

    internal val ready: HealthCheck = object : HealthCheck {
        override suspend fun check(): Result = readyResultat()
    }
    internal val healthy: HealthCheck = object : HealthCheck {
        override suspend fun check(): Result = healthyResultat()
    }

    init {
        check(unreadyEtterStoppetI.toMinutes() >= 1) { "unreadyEtterStoppetI må være over 1 minutt." }
        log.info("Starter $antallTråder consumer-tråd(er) på topic $topic")
        consumerStatus.labels(name).set(0.0)
        repeat(antallTråder) { executor.execute(::konsumerMedRestart) }
    }

    private fun konsumerMedRestart() {
        while (kjører.get()) {
            try {
                konsumer()
            } catch (e: Throwable) {
                if (!kjører.get()) break
                log.error("Consumer-tråd feilet. Kobler til på nytt om ${pauseFørRestart.seconds}s.", e)
                sov(pauseFørRestart)
            }
        }
        log.info("Consumer-tråd avsluttet")
    }

    private fun konsumer() {
        val consumer = consumerFactory(properties)
        consumers.add(consumer)
        try {
            consumer.subscribe(listOf(topic))
            trådTilkoblet()
            while (kjører.get()) {
                if (!behandle(consumer)) {
                    sov(pauseEtterFeil)
                }
            }
        } catch (e: WakeupException) {
            if (kjører.get()) throw e
        } finally {
            trådFrakoblet()
            consumers.remove(consumer)
            runCatching { consumer.close() }
        }
    }

    /** Returnerer false dersom en melding feilet, slik at kalleren kan pause før nytt forsøk. */
    private fun behandle(consumer: Consumer<String, String>): Boolean {
        val records = consumer.poll(POLL_TIMEOUT)
        var vellykket = true
        for (partisjon in records.partitions()) {
            var commitTil: Long? = null
            var spolTilbakeTil: Long? = null
            for (record in records.records(partisjon)) {
                if (!kjører.get()) {
                    spolTilbakeTil = record.offset()
                    break
                }
                try {
                    record.value()?.let { håndter(it) }
                    commitTil = record.offset() + 1
                } catch (e: WakeupException) {
                    throw e
                } catch (e: Exception) {
                    log.error(
                        "Feilet ved håndtering av melding på $topic-${partisjon.partition()} offset ${record.offset()}. Prøver igjen om ${pauseEtterFeil.toMillis()}ms.",
                        e
                    )
                    spolTilbakeTil = record.offset()
                    vellykket = false
                    break
                }
            }
            // Én commit per partisjon per poll-runde, etter at handleren er ferdig med meldingene
            commitTil?.let { consumer.commitSync(mapOf(partisjon to OffsetAndMetadata(it))) }
            spolTilbakeTil?.let { consumer.seek(partisjon, it) }
        }
        return vellykket
    }

    private fun trådTilkoblet() {
        if (trådetilkoblet.incrementAndGet() == antallTråder) {
            stoppet = null
            consumerStatus.labels(name).set(0.0)
        }
    }

    private fun trådFrakoblet() {
        trådetilkoblet.decrementAndGet()
        if (kjører.get()) {
            stoppet = LocalDateTime.now()
            consumerStatus.labels(name).set(1.0)
        }
    }

    private fun sov(varighet: Duration) = Thread.sleep(varighet.toMillis())

    internal fun stop() {
        if (!kjører.compareAndSet(true, false)) {
            log.info("Er allerede stoppet/stoppes.")
            return
        }
        log.info("Stopper consumer")
        stoppet = LocalDateTime.now()
        executor.shutdown()
        // Lar trådene avslutte selv først. Da rekker meldinger som er under behandling å bli
        // committet, i stedet for at wakeup() avbryter commitSync og gir unødvendig reprosessering.
        if (executor.awaitTermination(20, TimeUnit.SECONDS)) return

        log.info("Consumer-tråder er fortsatt opptatt, vekker dem.")
        consumers.forEach { runCatching { it.wakeup() } }
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            log.warn("Consumer-tråder ble ikke ferdige innen tidsfristen.")
            executor.shutdownNow()
        }
    }

    private fun stoppetI(): Duration = Duration.between(stoppet ?: LocalDateTime.now(), LocalDateTime.now())

    private fun readyResultat(): Result {
        val stoppetI = stoppet?.let { stoppetI() } ?: return Healthy(name, "Kjører som normalt.")
        return if (stoppetI >= unreadyEtterStoppetI) {
            UnHealthy(name, "Consumer har vært stoppet i ${stoppetI.toMinutes()} minutter.")
        } else {
            Healthy(
                name,
                "Consumer har vært stoppet i ${stoppetI.toMinutes()} minutter. Unready først etter ${unreadyEtterStoppetI.toMinutes()} minutter."
            )
        }
    }

    private fun healthyResultat(): Result {
        val stoppetI = stoppet?.let { stoppetI() } ?: return Healthy(name, "Kjører som normalt.")
        return UnHealthy(name, "Consumer har vært stoppet i ${stoppetI.toMinutes()} minutter.")
    }
}







