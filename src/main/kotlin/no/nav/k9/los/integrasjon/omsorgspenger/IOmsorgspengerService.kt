package no.nav.k9.los.integrasjon.omsorgspenger

interface IOmsorgspengerService {

    suspend fun hentOmsorgspengerSakDto(sakFnrDto: OmsorgspengerSakFnrDto): OmsorgspengerSakDto?

}
