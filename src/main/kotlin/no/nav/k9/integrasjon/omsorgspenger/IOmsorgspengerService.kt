package no.nav.k9.integrasjon.omsorgspenger

interface IOmsorgspengerService {

    suspend fun hentOmsorgspengerSakDto(sakFnrDto: OmsorgspengerSakFnrDto): OmsorgspengerSakDto?

}
