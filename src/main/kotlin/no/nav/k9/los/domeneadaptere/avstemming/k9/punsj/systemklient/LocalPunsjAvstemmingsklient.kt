package no.nav.k9.los.domeneadaptere.avstemming.k9.punsj.systemklient

import no.nav.k9.los.domeneadaptere.avstemming.k9.punsj.Journalposttilstand

class LocalPunsjAvstemmingsklient : PunsjAvstemmingsklient {
    override fun hentUferdigeJournalposter(): List<Journalposttilstand> {
        return emptyList()
    }
}