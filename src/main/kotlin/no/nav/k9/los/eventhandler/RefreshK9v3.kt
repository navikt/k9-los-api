package no.nav.k9.los.eventhandler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

class RefreshK9v3(
    val refreshK9v3Tjeneste: RefreshK9v3Tjeneste
) {

    fun CoroutineScope.start(channel: Channel<KøpåvirkendeHendelse>) =
        launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
            val hendelser = mutableListOf<KøpåvirkendeHendelse>()
            hendelser.add(channel.receive())
            while (true) {
                val hendelse = channel.tryReceive().getOrNull()
                if (hendelse == null) {
                    try {
                        ChannelMetrikker.timeSuspended("refresh_k9sak_v3") {
                            refreshK9v3Tjeneste.refreshK9(hendelser)
                            hendelser.clear()
                        }
                    } catch (e: Exception) {
                        log.error("Feilet ved refresh av oppgaver i k9-sak: " + hendelser.joinToString(", "), e)
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