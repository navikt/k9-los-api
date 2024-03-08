package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerIKlageDto
import no.nav.k9.sak.typer.AktørId
import java.util.*

class K9SakBerikerKlientLocal : K9SakBerikerInterfaceKludge {
    override fun hentBehandling(behandlingUUID: UUID): BehandlingMedFagsakDto? {
        val dto = BehandlingMedFagsakDto()
        dto.sakstype = FagsakYtelseType.OBSOLETE
        dto.behandlingResultatType = BehandlingResultatType.DELVIS_INNVILGET
        return dto
    }

    override fun berikKlage(påklagdBehandlingUUID: UUID): LosOpplysningerSomManglerIKlageDto? {
        val dto = LosOpplysningerSomManglerIKlageDto()
        dto.pleietrengendeAktørId = AktørId.dummy()
        dto.isUtenlandstilsnitt = false
        return dto
    }
}