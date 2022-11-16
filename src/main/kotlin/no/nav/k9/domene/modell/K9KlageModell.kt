package no.nav.k9.domene.modell

import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventKlageDto

data class K9KlageModell(
    val eventer: MutableList<BehandlingProsessEventKlageDto>
) {
}


