package no.nav.k9.los.domene.modell

import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventKlageDto

data class K9KlageModell(
    val eventer: MutableList<BehandlingProsessEventKlageDto>
) {
}


