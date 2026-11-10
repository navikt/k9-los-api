package no.nav.k9.los.domeneadaptere.avstemming.k9.saksbehandling.systemklient

import no.nav.k9.los.domeneadaptere.avstemming.k9.saksbehandling.Behandlingstilstand

class LocalSakAvstemmingsklient : SakAvstemmingsklient {
    override fun hentÅpneBehandlinger(): List<Behandlingstilstand> {
        return emptyList()
    }
}