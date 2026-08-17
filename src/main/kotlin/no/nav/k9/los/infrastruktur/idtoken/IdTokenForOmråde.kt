package no.nav.k9.los.infrastruktur.idtoken

sealed interface IdTokenForOmråde : IdToken {
    override val jwt: JWTToken
    override fun getNavIdent(): String = jwt.NAVident
    override fun getName(): String = jwt.name
    override fun getUsername(): String = jwt.preferredUsername
}
