package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.ChannelMetrikker
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

class RefreshK9v3(
    val refreshK9v3Tjeneste: RefreshK9v3Tjeneste
) {

    fun CoroutineScope.start(channel: Channel<KøpåvirkendeHendelse>) =
        launch(Dispatchers.IO) {
            val hendelser = mutableSetOf<KøpåvirkendeHendelse>()
            hendelser.add(channel.receive())
            while (true) {
                val hendelse = channel.tryReceive().getOrNull()
                if (hendelse == null) {
                    try {
                        val refreshUtført = ChannelMetrikker.timeSuspended("refresh_k9sak_v3") {
                            log.info("Behandler ${hendelser.size} oppgaver")
                            refreshK9v3Tjeneste.refreshK9(hendelser.toList())
                        }
                        hendelser.clear()
                        if (refreshUtført == RefreshK9v3Tjeneste.RefreshUtført.ALLE_KØER) {
                            //ta litt pause for å ikke lage unødvendig høy last
                            //TODO tilpasse når vi har fått erfaring fra prod
                            //kan fjernes dersom vi får på plass å bare hente oppgaver fra køer som er direkte påvirket
                            delay(15.seconds)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.error("Feilet ved refresh av oppgaver i k9-sak: " + hendelser.joinToString(", "), e)
                    } catch (t: Throwable) {
                        log.error(
                            "Feilet hardt (Throwable) ved refresh av oppgaver (v3) mot k9-sak, avslutter tråden",
                            t
                        )
                        throw t
                    }
                    hendelser.add(channel.receive())
                } else {
                    hendelser.add(hendelse)
                }
            }
        }


    companion object {
        val log = LoggerFactory.getLogger("RefreshK9v3")
    }

}