package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import no.nav.k9.sak.kontrakt.behandling.BehandlingDto
import java.util.*

//Stygg konstruksjon for mocking frem til vi får lagt k9-los inn i k9-verdikjede
interface K9SakBerikerInterfaceKludge {
    fun hentBehandling(behandlingUUID: UUID): BehandlingDto
}