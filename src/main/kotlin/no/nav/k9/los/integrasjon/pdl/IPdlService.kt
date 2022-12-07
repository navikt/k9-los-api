package no.nav.k9.los.integrasjon.pdl

interface IPdlService {
    suspend fun person(aktorId: String): PersonPdlResponse

    suspend fun identifikator(fnummer: String): PdlResponse
}
