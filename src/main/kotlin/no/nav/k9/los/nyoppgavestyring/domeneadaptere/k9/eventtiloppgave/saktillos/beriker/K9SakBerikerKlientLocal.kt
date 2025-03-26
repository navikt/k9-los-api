package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import java.util.*

class K9SakBerikerKlientLocal : K9SakBerikerInterfaceKludge {
    override fun hentBehandling(behandlingUUID: UUID, antallForsøk: Int): BehandlingMedFagsakDto? {
        val dto = BehandlingMedFagsakDto()
        dto.sakstype = FagsakYtelseType.OBSOLETE
        dto.behandlingResultatType = BehandlingResultatType.DELVIS_INNVILGET
        return dto
    }
}