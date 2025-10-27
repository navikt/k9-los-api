package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.concurrent.thread

class EventlagerKonverteringsjobb(
    private val transactionalManager: TransactionalManager,
    private val punsjEventRepository: K9PunsjEventRepository,
    private val klageEventRepository: K9KlageEventRepository,
    private val tilbakeEventRepository: K9TilbakeEventRepository,
    private val sakEventRepository: K9SakEventRepository,
    private val eventlagerKonverteringsservice: EventlagerKonverteringsservice,
) {
    private val log: Logger = LoggerFactory.getLogger(EventlagerKonverteringsjobb::class.java)
    private val TRÅDNAVN = "eventlagerKonvertering"

    fun kjørEventlagerKonvertering() {
        log.info("Spiller av eventer i gammel løsning og skriver til ny modell")
        thread(
            start = true,
            isDaemon = true,
            name = TRÅDNAVN,
        ) {
            spillAvEventer()
        }
    }

    fun spillAvEventer() {
        run { // PUNSJ
            val ukonverterteEventer = finnUkonvertertePunsjEventer()
            log.info("Starter konvertering av punsjeventer. Funnet ${ukonverterteEventer.size} ukonverterte oppgaver.")
            var i = 0L
            for (uuid in ukonverterteEventer) {
                transactionalManager.transaction { tx ->
                    val punsjModell = punsjEventRepository.hentMedLås(tx, UUID.fromString(uuid))
                    for (event in punsjModell.eventer) {
                        eventlagerKonverteringsservice.konverterEvent(event, tx, true)
                    }
                }
                i++
                if (i % 1000 == 0L) {
                    log.info("Konvertert $i punsjoppgaver")
                }
            }

            log.info("Konvertering av punsjeventer ferdig. Konvertert $i oppgaver")
        }

        run { // TILBAKE
            val ukonverterteEventer = finnUkonverterteTilbakeEventer()
            log.info("Starter konvertering av k9-tilbake-eventer. Funnet ${ukonverterteEventer.size} ukonverterte oppgaver.")
            var i = 0L
            for (uuid in ukonverterteEventer) {
                transactionalManager.transaction { tx ->
                    val tilbakeModell = tilbakeEventRepository.hentMedLås(tx, UUID.fromString(uuid))
                    for (event in tilbakeModell.eventer) {
                        eventlagerKonverteringsservice.konverterEvent(event, tx, true)
                    }
                }
                i++
                if (i % 1000 == 0L) {
                    log.info("Konvertert $i k9-tilbake-oppgaver")
                }
            }

            log.info("Konvertering av k9-tilbake-eventer ferdig. Konvertert $i oppgaver")
        }

        run { // KLAGE
            val ukonverterteEventer = finnUkonverterteKlageEventer()
            log.info("Starter konvertering av k9-klage-eventer. Funnet ${ukonverterteEventer.size} ukonverterte oppgaver.")
            var i = 0L
            for (uuid in ukonverterteEventer) {
                transactionalManager.transaction { tx ->
                    val klageModell = klageEventRepository.hentMedLås(tx, UUID.fromString(uuid))
                    for (event in klageModell.eventer) {
                        eventlagerKonverteringsservice.konverterEvent(event, tx, true)
                    }
                }
                i++
                if (i % 1000 == 0L) {
                    log.info("Konvertert $i k9-klage-oppgaver")
                }
            }
            log.info("Konvertering av k9-klage-eventer ferdig. Konvertert $i oppgaver")
        }

        run { // SAK
            val ukonverterteEventer = finnUkonverterteSakEventer()
            log.info("Starter konvertering av k9-sak-eventer. Funnet ${ukonverterteEventer.size} ukonverterte oppgaver.")
            var i = 0L
            for (uuid in ukonverterteEventer) {
                transactionalManager.transaction { tx ->
                    val sakModell = sakEventRepository.hentMedLås(tx, UUID.fromString(uuid))
                    for (event in sakModell.eventer) {
                        eventlagerKonverteringsservice.konverterEvent(event, tx, true)
                    }
                }
                i++
                if (i % 1000 == 0L) {
                    log.info("Konvertert $i k9-sak-oppgaver")
                }
            }
            log.info("Konvertering av k9-sak-eventer ferdig. Konvertert $i oppgaver")
        }
    }

    private fun finnUkonverterteSakEventer(): List<String> {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                select id
                from behandling_prosess_events_k9 egml
            """.trimIndent(),
                    mapOf(
                        "now" to LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
                    )
                ).map { row ->
                    row.string("id")
                }.asList
            )
        }
    }

    private fun finnUkonverterteKlageEventer(): List<String> {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                select id
                from behandling_prosess_events_klage egml
            """.trimIndent(),
                    mapOf(
                        "now" to LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
                    )
                ).map { row ->
                    row.string("id")
                }.asList
            )
        }
    }

    private fun finnUkonverterteTilbakeEventer(): List<String> {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                select id
                from behandling_prosess_events_tilbake egml
            """.trimIndent(),
                    mapOf(
                        "now" to LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
                    )
                ).map { row ->
                    row.string("id")
                }.asList
            )
        }
    }

    private fun finnUkonvertertePunsjEventer(): List<String> {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                select id
                from behandling_prosess_events_k9_punsj egml
            """.trimIndent(),
                    mapOf(
                        "now" to LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
                    )
                ).map { row ->
                    row.string("id")
                }.asList
            )
        }
    }
}