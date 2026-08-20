package no.nav.k9.los.infrastruktur.pdl

import no.nav.k9.los.infrastruktur.kontekst.InnloggetBruker

interface IPdlService {
    suspend fun person(aktorId: String, bruker: InnloggetBruker): PersonPdlResponse

    suspend fun identifikator(fnummer: String, bruker: InnloggetBruker): PdlResponse
}
