package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker

import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerIKlageDto
import java.util.*

//Stygg konstruksjon for mocking frem til vi får lagt k9-los inn i k9-verdikjede
interface K9SakBerikerInterfaceKludge {
    fun hentBehandling(behandlingUUID: UUID, antallForsøk: Int = 3): BehandlingMedFagsakDto?

    fun berikKlage(påklagdBehandlingUUID: UUID, antallForsøk: Int = 3): LosOpplysningerSomManglerIKlageDto?
}