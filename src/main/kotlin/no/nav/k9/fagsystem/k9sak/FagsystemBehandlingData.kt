package no.nav.k9.fagsystem.k9sak

import no.nav.k9.domene.modell.Aksjonspunkter
import no.nav.k9.domene.modell.alleAksjonspunkter
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

data class FagsystemBehandlingData(
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
        private val log = LoggerFactory.getLogger(FagsystemBehandlingData::class.java)

        fun opprettFra(eksternReferanse: UUID, event: BehandlingProsessEventDto): FagsystemBehandlingData {
            return FagsystemBehandlingData(
                eksternReferanse = eksternReferanse,
                relarelatertPartAktørId = event.relatertPartAktørId,
                pleietrengendeAktør = event.pleietrengendeAktørId,
                kravType = null,
                aksjonspunkter = event.alleAksjonspunkter()
            )
        }
    }

}

enum class KravType {
    SØKNAD,
    INNTEKTSMELDING
}