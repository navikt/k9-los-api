package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.beriker

import no.nav.k9.klage.kodeverk.behandling.BehandlingType
import no.nav.k9.klage.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerHistoriskIKlageDto
import java.util.*

class K9KlageBerikerKlientLocal : K9KlageBerikerInterfaceKludge {
    override fun hentFraK9Klage(
        påklagdBehandlingUUID: UUID,
        antallForsøk: Int
    ): LosOpplysningerSomManglerHistoriskIKlageDto {
        return LosOpplysningerSomManglerHistoriskIKlageDto(BehandlingType.FØRSTEGANGSSØKNAD)
    }
}