package no.nav.k9.fagsystem.k9sak

import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.Aksjonspunkter
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

data class BehandlingdataK9Sak(
    val eksternReferanse: UUID,
    val pleietrengendeAktør: String?,
    val relarelatertPartAktørId: String?,
    val kravType: KravType?,
    val aksjonspunkter: Aksjonspunkter,
) {

    private var ferdigstilt: LocalDateTime? = null

    fun erFerdigstilt(): Boolean {
        return ferdigstilt != null
    }

    fun ferdigstill(tidspunkt: LocalDateTime) {
        if (ferdigstilt == null) {
            ferdigstilt = tidspunkt
        } else {
            log.warn("Forsøker å ferdigstille allerede ferdigstilt behandling. $ferdigstilt, $tidspunkt")
        }
    }

    fun hentBerørteParter(): Set<String> {
        val berørteParter = mutableSetOf<String>()
        pleietrengendeAktør?.run { berørteParter.add(this) }
        relarelatertPartAktørId?.run { berørteParter.add(this) }
        return berørteParter
    }

    companion object {
        private val log = LoggerFactory.getLogger(BehandlingdataK9Sak::class.java)

        fun opprettFra(eksternReferanse: UUID, oppgave: Oppgave): BehandlingdataK9Sak {
            return BehandlingdataK9Sak(
                eksternReferanse = eksternReferanse,
                relarelatertPartAktørId = oppgave.relatertPartAktørId,
                pleietrengendeAktør = oppgave.pleietrengendeAktørId,
                kravType = null,
                aksjonspunkter = oppgave.aksjonspunkter
            )
        }
    }

}

enum class KravType {
    SØKNAD,
    INNTEKTSMELDING
}