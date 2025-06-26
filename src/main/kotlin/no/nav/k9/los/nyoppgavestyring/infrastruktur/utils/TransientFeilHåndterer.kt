package no.nav.k9.los.nyoppgavestyring.infrastruktur.utils

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.TransientException
import org.slf4j.LoggerFactory
import java.io.InterruptedIOException
import java.net.SocketException
import java.sql.SQLException
import java.sql.SQLNonTransientException
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TransientFeilHåndterer(
    val pauseInitiell: Duration = 100.toDuration(DurationUnit.MILLISECONDS),
    val pauseMaks: Duration = 1.toDuration(DurationUnit.MINUTES),
    val warningEtter: Duration = 1.toDuration(DurationUnit.MINUTES),
    val giOppEtter: Duration = 1.toDuration(DurationUnit.HOURS),
) {

    fun utfør(beskrivelse: String, operasjon: () -> Unit) {
        var pause = pauseInitiell
        var pauseTotal = Duration.ZERO
        var forsøk = 1
        do {
            try {
                return operasjon.invoke()
            } catch (e: Exception) {
                if (!antarErTransientFeil(e)) {
                    throw e
                }
                if (pauseTotal > giOppEtter) {
                    throw RuntimeException("Operasjonen $beskrivelse feilet. Har forsøkt $forsøk ganger og ventet totalt $pauseTotal", e)
                } else if (pauseTotal > warningEtter) {
                    log.warn("Operasjonen $beskrivelse feilet, venter og prøver på nytt. Har forsøkt $forsøk ganger og ventet totalt $pauseTotal", e)
                } else {
                    log.info("Operasjonen $beskrivelse feilet, venter og prøver på nytt. Har forsøkt $forsøk ganger og ventet totalt $pauseTotal")
                }
            }
            Thread.sleep(pause.inWholeMilliseconds)
            pauseTotal += pause
            forsøk++
            pause = minOf(pauseMaks, pause * 2)
        } while (true)
    }

    private fun antarErTransientFeil(e: Exception): Boolean {
        if (e is SQLNonTransientException) {
            return false
        }
        return e is InterruptedIOException
                || e is SocketException
                || e is SQLException
                || e is TransientException
    }

    companion object {
        val log = LoggerFactory.getLogger(TransientFeilHåndterer::class.java)
    }
}