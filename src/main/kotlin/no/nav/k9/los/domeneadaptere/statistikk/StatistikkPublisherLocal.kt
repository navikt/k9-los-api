package no.nav.k9.los.domeneadaptere.statistikk

import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import org.slf4j.LoggerFactory

class StatistikkPublisherLocal(
    private val navn: String,
) : StatistikkPublisher {

    private companion object {
        private val log = LoggerFactory.getLogger(StatistikkPublisherLocal::class.java)
    }

    override fun publiserAsynkront(nøkkel: String, melding: Any) {
        log.info(
            "Statistikk {} key={}: {}",
            navn,
            nøkkel,
            LosObjectMapper.instance.writeValueAsString(melding),
        )
    }

    override fun flushOgValider() {}
}
