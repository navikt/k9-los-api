package no.nav.k9.los.infrastruktur.pdl

import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde

interface IPdlService {
    suspend fun person(aktorId: String, brukerkontekst: BrukerkontekstMedOmråde): PersonPdlResponse
    suspend fun identifikator(fnummer: String, brukerkontekst: BrukerkontekstMedOmråde): PdlResponse
}
