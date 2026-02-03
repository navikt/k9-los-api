package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.punsj.systemklient

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Journalposttilstand

class LocalAvstemmingsklient : Avstemmingsklient {
    override fun hentÅpneBehandlinger(): List<Journalposttilstand> {
        return emptyList()
    }
}