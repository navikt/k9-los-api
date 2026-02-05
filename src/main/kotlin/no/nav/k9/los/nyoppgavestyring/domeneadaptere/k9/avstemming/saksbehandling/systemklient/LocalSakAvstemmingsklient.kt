package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.systemklient

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.Behandlingstilstand

class LocalSakAvstemmingsklient : SakAvstemmingsklient {
    override fun hentÅpneBehandlinger(): List<Behandlingstilstand> {
        return emptyList()
    }
}