package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.beriker

import no.nav.k9.klage.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerHistoriskIKlageDto
import java.util.UUID

interface K9KlageBerikerInterfaceKludge {
    fun hentFraK9Klage(påklagdBehandlingUUID: UUID, antallForsøk: Int = 3): LosOpplysningerSomManglerHistoriskIKlageDto?
}