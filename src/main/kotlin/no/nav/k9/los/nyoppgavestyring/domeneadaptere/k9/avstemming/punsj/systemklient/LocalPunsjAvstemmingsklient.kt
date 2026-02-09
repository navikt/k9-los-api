package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.punsj.systemklient

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.punsj.Journalposttilstand

class LocalPunsjAvstemmingsklient : PunsjAvstemmingsklient {
    override fun hentUferdigeJournalposter(): List<Journalposttilstand> {
        return emptyList()
    }
}