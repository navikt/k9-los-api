package no.nav.k9.los.eventhandler

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.JobbMetrikker
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
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
        OpentelemetrySpanUtil.span("sjekkReserverteJobb", emptyMap())  {
            JobbMetrikker.time("sjekk_reserverte") {
                for (saksbehandler in saksbehandlerRepository.hentAlleSaksbehandlereInkluderKode6()) {
                    runBlocking { reservasjonRepository.hentOgFjernInaktiveReservasjoner(saksbehandler.reservasjoner, saksbehandler.brukerIdent) }
                }
            }
        }
    }
}