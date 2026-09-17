package no.nav.k9.los.infrastruktur.idtoken

data class IdTokenLocal(
    override val value: String = "",

) : IIdToken {
    override val jwt: Nothing? = null
    override fun getTokenId(): String = "token-id"
    override fun getNavIdent(): String = "Z123456"
    override fun getName(): String = "Saksbehandler Sara"
    override fun getUsername(): String = "saksbehandler.sara@nav.no"
}
