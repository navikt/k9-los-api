package no.nav.k9.los.domene.modell

import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse

data class K9KlageModell(
    val eventer: MutableList<KlagebehandlingProsessHendelse>
) {
}


