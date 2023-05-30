package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.sak.kontrakt.behandling.BehandlingDto
import java.util.*

class K9SakBerikerKlientLocal : K9SakBerikerInterfaceKludge {
    override fun hentBehandling(behandlingUUID: UUID): BehandlingDto {
        val dto = BehandlingDto()
        dto.behandlingResultatType = BehandlingResultatType.DELVIS_INNVILGET
        return dto
    }
}