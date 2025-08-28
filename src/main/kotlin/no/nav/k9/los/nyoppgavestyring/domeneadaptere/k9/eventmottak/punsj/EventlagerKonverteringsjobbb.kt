package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.concurrent.thread

class EventlagerKonverteringsjobbb(
    private val transactionalManager: TransactionalManager,
    private val punsjEventRepository: K9PunsjEventRepository,
    private val eventlagerKonverteringsservice: EventlagerKonverteringsservice,
) {
    private val log: Logger = LoggerFactory.getLogger(EventlagerKonverteringsjobbb::class.java)
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
        val ukonverterteEventer = finnUkonverterteEventer()

        for (uuid in ukonverterteEventer) {
            transactionalManager.transaction { tx ->
                punsjEventRepository.hentMedLås(tx, UUID.fromString(uuid))
                eventlagerKonverteringsservice.konverterOppgave(uuid, tx)
            }
        }
    }

    private fun finnUkonverterteEventer(): List<String> {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                select id
                from behandling_prosess_events_k9_punsj egml
                where not exists (select ekstern_id from eventlager_punsj eny where egml.id = eny.ekstern_id)
            """.trimIndent(),
                    mapOf(
                        "now" to LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
                    )
                ).map { row ->
                    row.string("reservasjonsnokkel")
                }.asList
            )
        }
    }
}