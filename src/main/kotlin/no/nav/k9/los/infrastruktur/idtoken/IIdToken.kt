package no.nav.k9.los.infrastruktur.idtoken

interface IIdToken {
    val value: String
    fun getTokenId(): String
    fun getNavIdent(): String
    fun getName(): String
    fun getPreferredUsername(): String
}
