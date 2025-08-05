package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.sak.systemklient

import no.nav.k9.sak.kontrakt.avstemming.produksjonsstyring.Behandlingstilstand

interface K9SakAvstemmingsklient {
    fun hentÅpneBehandlinger(): List<Behandlingstilstand>
}