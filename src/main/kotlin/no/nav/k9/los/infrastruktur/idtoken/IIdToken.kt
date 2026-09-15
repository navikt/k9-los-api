package no.nav.k9.los.infrastruktur.idtoken

interface IIdToken {
    val value: String
    val jwt: JWTToken?
    fun getTokenId(): String
    fun getNavIdent(): String
    fun getName(): String
    fun getUsername(): String
}