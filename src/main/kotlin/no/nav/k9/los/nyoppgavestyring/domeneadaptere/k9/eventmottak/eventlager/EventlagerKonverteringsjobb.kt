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
    private val TRÅDNAVN = "eventlagerKonverteringPunsj"

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

    private fun spillAvEventer() {
        run { // PUNSJ
            val ukonverterteEventer = finnUkonvertertePunsjEventer()

            for (uuid in ukonverterteEventer) {
                transactionalManager.transaction { tx ->
                    punsjEventRepository.hentMedLås(tx, UUID.fromString(uuid))
                    eventlagerKonverteringsservice.konverterOppgave(uuid, Fagsystem.PUNSJ, tx)
                }
            }
        }

        run { // TILBAKE
            val ukonverterteEventer = finnUkonverterteTilbakeEventer()

            for (uuid in ukonverterteEventer) {
                transactionalManager.transaction { tx ->
                    tilbakeEventRepository.hentMedLås(tx, UUID.fromString(uuid))
                    eventlagerKonverteringsservice.konverterOppgave(uuid, Fagsystem.TILBAKE, tx)
                }
            }
        }

        run { // KLAGE
            val ukonverterteEventer = finnUkonverterteKlageEventer()

            for (uuid in ukonverterteEventer) {
                transactionalManager.transaction { tx ->
                    klageEventRepository.hentMedLås(tx, UUID.fromString(uuid))
                    eventlagerKonverteringsservice.konverterOppgave(uuid, Fagsystem.KLAGE, tx)
                }
            }
        }

        run { // SAK
            val ukonverterteEventer = finnUkonverterteSakEventer()

            for (uuid in ukonverterteEventer) {
                transactionalManager.transaction { tx ->
                    sakEventRepository.hentMedLås(tx, UUID.fromString(uuid))
                    eventlagerKonverteringsservice.konverterOppgave(uuid, Fagsystem.SAK, tx)
                }
            }
        }
    }

    private fun finnUkonverterteSakEventer(): List<String> {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                select id
                from behandling_prosess_events_k9 egml
                where not exists (select ekstern_id from eventlager eny where egml.id = eny.ekstern_id and eny.fagsystem = "SAK")
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
                from behandling_prosess_events_k9_klage egml
                where not exists (select ekstern_id from eventlager eny where egml.id = eny.ekstern_id and eny.fagsystem = "KLAGE")
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
                from behandling_prosess_events_k9_tilbake egml
                where not exists (select ekstern_id from eventlager eny where egml.id = eny.ekstern_id and eny.fagsystem = "TILBAKE")
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
                where not exists (select ekstern_id from eventlager eny where egml.id = eny.ekstern_id and eny.fagsystem = "PUNSJ")
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