package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker

import no.nav.k9.klage.kodeverk.behandling.BehandlingType
import no.nav.k9.klage.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerHistoriskIKlageDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerIKlageDto
import no.nav.k9.sak.typer.AktørId
import java.util.*

class K9KlageBerikerKlientLocal : K9KlageBerikerInterfaceKludge {
    override fun hentFraK9Klage(
        påklagdBehandlingUUID: UUID,
        antallForsøk: Int
    ): LosOpplysningerSomManglerHistoriskIKlageDto {
        return LosOpplysningerSomManglerHistoriskIKlageDto(BehandlingType.FØRSTEGANGSSØKNAD)
    }

    override fun hentFraK9Sak(påklagdBehandlingUUID: UUID, antallForsøk: Int): LosOpplysningerSomManglerIKlageDto? {
        val dto = LosOpplysningerSomManglerIKlageDto()
        dto.pleietrengendeAktørId = AktørId.dummy()
        dto.isUtenlandstilsnitt = false
        return dto
    }
}