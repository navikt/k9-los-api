package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.punsj.systemklient

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.punsj.Journalposttilstand

interface PunsjAvstemmingsklient {
    fun hentUferdigeJournalposter(): List<Journalposttilstand>
}