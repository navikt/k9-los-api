package no.nav.k9.los.testsupport.jws

interface Issuer {
    fun getPublicJwk() : String
    fun getIssuer() : String
}
