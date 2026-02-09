package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.systemklient

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.Behandlingstilstand

interface SakAvstemmingsklient {
    fun hentÅpneBehandlinger(): List<Behandlingstilstand>
}