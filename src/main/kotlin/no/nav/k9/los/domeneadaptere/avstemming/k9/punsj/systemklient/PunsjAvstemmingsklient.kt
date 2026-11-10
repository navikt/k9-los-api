package no.nav.k9.los.domeneadaptere.avstemming.k9.punsj.systemklient

import no.nav.k9.los.domeneadaptere.avstemming.k9.punsj.Journalposttilstand

interface PunsjAvstemmingsklient {
    fun hentUferdigeJournalposter(): List<Journalposttilstand>
}