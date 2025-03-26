package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker

import no.nav.k9.klage.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerHistoriskIKlageDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerIKlageDto
import java.util.*

interface K9KlageBerikerInterfaceKludge {
    fun hentFraK9Klage(påklagdBehandlingUUID: UUID, antallForsøk: Int = 3): LosOpplysningerSomManglerHistoriskIKlageDto?

    fun hentFraK9Sak(påklagdBehandlingUUID: UUID, antallForsøk: Int = 3): LosOpplysningerSomManglerIKlageDto?
}