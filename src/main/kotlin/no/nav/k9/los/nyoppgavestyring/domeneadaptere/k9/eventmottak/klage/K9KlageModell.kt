package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage

import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse

data class K9KlageModell(
    val eventer: MutableList<KlagebehandlingProsessHendelse>
) {
}


