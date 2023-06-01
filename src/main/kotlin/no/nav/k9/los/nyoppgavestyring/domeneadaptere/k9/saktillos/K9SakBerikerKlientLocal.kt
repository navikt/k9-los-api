package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.sak.kontrakt.behandling.BehandlingDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import java.util.*

class K9SakBerikerKlientLocal : K9SakBerikerInterfaceKludge {
    override fun hentBehandling(behandlingUUID: UUID): BehandlingMedFagsakDto {
        val dto = BehandlingMedFagsakDto()
        dto.sakstype = FagsakYtelseType.OBSOLETE
        dto.behandlingResultatType = BehandlingResultatType.DELVIS_INNVILGET
        return dto
    }
}