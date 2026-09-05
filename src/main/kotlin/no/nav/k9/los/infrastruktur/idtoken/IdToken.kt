package no.nav.k9.los.infrastruktur.idtoken

interface IdToken {
    val value: String
    fun getNavIdent(): String
    fun getName(): String
    fun getPreferredUsername(): String
}
