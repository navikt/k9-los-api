package no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.saktillos.beriker

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import java.util.*

class K9SakSystemKlientLocal : K9SakSystemKlientInterfaceKludge {
    override fun hentBehandling(behandlingUUID: UUID, antallForsøk: Int): BehandlingMedFagsakDto? {
        val dto = BehandlingMedFagsakDto()
        dto.sakstype = FagsakYtelseType.OBSOLETE
        dto.behandlingResultatType = BehandlingResultatType.DELVIS_INNVILGET
        return dto
    }
}