package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

class ReservasjonKonverteringJobb(
    private val config: Configuration,
    private val reservasjonRepository: ReservasjonRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonOversetter: ReservasjonOversetter,
    private val saksbehandlerRepository: SaksbehandlerRepository,
) {

    private val log: Logger = LoggerFactory.getLogger(ReservasjonKonverteringJobb::class.java)
    private val TRÅDNAVN = "reservasjonKonvertering"

    fun kjør() {
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
        val tidKjøringStartet = System.currentTimeMillis() //TODO: Telleverk og logge fremdrift

        val reservasjonIder = reservasjonRepository.hentAlleReservasjonUUID()
        log.info("Fant ${reservasjonIder.size} behandlinger")

        reservasjonIder.forEach { gammelReservasjonUuid ->
            val reservasjonV1 = reservasjonRepository.hent(gammelReservasjonUuid)
            val saksbehandler = runBlocking {
                saksbehandlerRepository.finnSaksbehandlerMedIdent(reservasjonV1.reservertAvIdent)
            }!!
            //TODO filtrer bort gamle og/eller ugyldige reservasjoner?
            if (reservasjonV1.reservertTil == null) {
                return //Logisk slettet reservasjon. Migreres ikke
            }
            val oppgaveV1 = oppgaveRepository.hent(reservasjonV1.oppgave)

            val flyttetAvSaksbehandlerId = reservasjonV1.flyttetAvIdent?.let {
                runBlocking {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it)!!.id!!
                }
            }

            reservasjonOversetter.taNyReservasjonFraGammelKontekst(
                oppgaveV1 = oppgaveV1,
                reserverForSaksbehandlerId = saksbehandler.id!!,
                reservertTil = reservasjonV1.reservertTil!!,
                utførtAvSaksbehandlerId = flyttetAvSaksbehandlerId ?: saksbehandler.id!!,
                kommentar = reservasjonV1.begrunnelse,
            )
        }
    }
}