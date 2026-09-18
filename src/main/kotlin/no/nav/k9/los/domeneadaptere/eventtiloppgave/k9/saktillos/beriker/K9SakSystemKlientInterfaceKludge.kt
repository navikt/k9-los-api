package no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.beriker

import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import java.util.*

//Stygg konstruksjon for mocking frem til vi får lagt k9-los inn i k9-verdikjede
interface K9SakSystemKlientInterfaceKludge {
    fun hentBehandling(behandlingUUID: UUID, antallForsøk: Int = 3): BehandlingMedFagsakDto?
}