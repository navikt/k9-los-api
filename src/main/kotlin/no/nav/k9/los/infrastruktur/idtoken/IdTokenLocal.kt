package no.nav.k9.los.infrastruktur.idtoken

data class IdTokenLocal(
    override val value: String = "",
) : IdToken {
    override fun getNavIdent(): String = "Z123456"
    override fun getName(): String = "saksbehandler@nav.no"
    override fun getPreferredUsername(): String = "saksbehandler@nav.no"
}
