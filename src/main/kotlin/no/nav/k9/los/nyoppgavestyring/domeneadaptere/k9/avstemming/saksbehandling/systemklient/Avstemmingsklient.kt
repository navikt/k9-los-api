package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.systemklient

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Behandlingstilstand

interface Avstemmingsklient {
    fun hentÅpneBehandlinger(): List<Behandlingstilstand>
}