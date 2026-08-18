package no.nav.k9.los.infrastruktur.idtoken

interface IdToken {
    val value: String
    val groups: Set<String>
    fun getNavIdent(): String
    fun getName(): String
    fun getUsername(): String
}
