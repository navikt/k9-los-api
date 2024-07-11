package no.nav.k9.los.eventhandler

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.jobber.JobbMetrikker
import java.util.*
import kotlin.concurrent.fixedRateTimer

fun sjekkReserverteJobb(
    reservasjonRepository: ReservasjonRepository,
    saksbehandlerRepository: SaksbehandlerRepository
): Timer {
    return fixedRateTimer(
        name = "sjekkReserverteTimer", daemon = true,
        initialDelay = 0, period = 900 * 1000
    ) {
        JobbMetrikker.time("sjekk_reserverte") {
            for (saksbehandler in saksbehandlerRepository.hentAlleSaksbehandlereIkkeTaHensyn()) {
                runBlocking { reservasjonRepository.hent(saksbehandler.reservasjoner, saksbehandler.brukerIdent) }
            }
        }
    }
}