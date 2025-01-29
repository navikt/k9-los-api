package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotliquery.queryOf
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.reservasjon.ManglerTilgangException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonUtløptException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.concurrent.thread

class ReservasjonKonverteringJobb(
    private val config: Configuration,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val oppgaveRepository: OppgaveRepository,
) {

    private val log: Logger = LoggerFactory.getLogger(ReservasjonKonverteringJobb::class.java)
    private val TRÅDNAVN = "reservasjonKonvertering"

    private fun finnLegacyreservasjoner(): List<String> {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                select reservasjonsnokkel
                from reservasjon_v3 rv
                where reservasjonsnokkel like 'legacy%'
                   and annullert_for_utlop = false
                   and lower(gyldig_tidsrom) <= :now
                   and upper(gyldig_tidsrom) > :now
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

    fun kjørReservasjonskonvertering() {
        if (config.nyOppgavestyringAktivert()) {
            log.info("Spiller av reservasjoner i gammel løsning og skriver til ny modell")
            thread(
                start = true,
                isDaemon = true,
                name = TRÅDNAVN,
            ) {
                spillAvReservasjoner()
            }
        }
    }

    fun spillAvReservasjoner() {
        log.info("Starter avspilling av reservasjoner")

        val reservasjonsnøkler = finnLegacyreservasjoner()
        log.info("Fant ${reservasjonsnøkler.size} reservasjoner")
        var reservasjonTeller = 0L

        for (nokkel in reservasjonsnøkler) {
            transactionalManager.transaction { tx ->
                val legacyReservasjon = reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(nokkel, tx)!!
                try {
                    reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(nokkel, "Annullert av konvertering", legacyReservasjon.reservertAv, tx = tx)

                    val oppgave = oppgaveRepository.hentNyesteOppgaveForEksternId(tx, "K9", nokkel.drop("legacy_".length))
                    val reservasjon = reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                        reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                        reserverForId = legacyReservasjon.reservertAv,
                        gyldigFra = legacyReservasjon.gyldigFra,
                        gyldigTil = legacyReservasjon.gyldigTil,
                        kommentar = legacyReservasjon.kommentar,
                        utføresAvId = legacyReservasjon.reservertAv,
                        tx = tx,
                    )
                    loggFremgangForHver100(reservasjonTeller++, "Konvertert $reservasjonTeller reservasjoner")
                } catch (e: ReservasjonUtløptException) {
                    log.info("Reservasjonen har blitt ugyldig før konvertering. Hopper over")
                } catch (e: ManglerTilgangException) {
                    log.error("Konvertering av reservasjon $nokkel feilet pga manglende tilgang", e.message)
                }
            }

        }
        log.info("Antall reservasjoner funnet: ${reservasjonsnøkler.size}, antall konverterte: $reservasjonTeller")
        log.info("Reservasjonskonvertering ferdig")
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }
}