package no.nav.k9.los.domeneadaptere.avstemming.k9.saksbehandling.systemklient

import no.nav.k9.los.domeneadaptere.avstemming.k9.saksbehandling.Behandlingstilstand

interface SakAvstemmingsklient {
    fun hentÅpneBehandlinger(): List<Behandlingstilstand>
}