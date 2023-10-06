package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.Reservasjon
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.tjenester.saksbehandler.oppgave.forskyvReservasjonsDatoBakover
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.concurrent.thread

class ReservasjonKonverteringJobb(
    private val config: Configuration,
    private val reservasjonRepository: ReservasjonRepository,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonOversetter: ReservasjonOversetter,
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

    private fun spillAvReservasjoner() {

        log.info("Stareter avspilling av reservasjoner")
        val tidKjøringStartet = System.currentTimeMillis() //TODO: Telleverk og logge fremdrift

        val reservasjonIder = reservasjonRepository.hentAlleReservasjonUUID()
        log.info("Fant ${reservasjonIder.size} behandlinger")

        reservasjonIder.forEach { gammelReservasjonUuid ->
            val reservasjonV1 = reservasjonRepository.hent(gammelReservasjonUuid)
            //TODO filtrer bort gamle og/eller ugyldige reservasjoner?
            if (reservasjonV1.reservertTil == null) {
                return //Logisk slettet reservasjon. Migreres ikke
            }
            val oppgaveV1 = oppgaveRepository.hent(reservasjonV1.oppgave)
            reservasjonOversetter.taNyReservasjonFraGammelKontekst(
                oppgaveV1 = oppgaveV1,
                reservertAvEpost = reservasjonV1.reservertAv,
                reservertTil = reservasjonV1.reservertTil!!,
                utførtAvIdent = reservasjonV1.flyttetAv,
                kommentar = reservasjonV1.begrunnelse,
            )
        }
    }
}