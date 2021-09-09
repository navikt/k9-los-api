package no.nav.k9.integrasjon.pdl

interface IPdlService {
    suspend fun person(aktorId: String): PersonPdlResponse

    suspend fun identifikator(fnummer: String): PdlResponse
}
