package no.nav.k9.los.infrastruktur.idtoken

data class IdTokenUng(
    override val value: String,
    override val jwt: JWTToken = JWTToken.fra(value),
) : IdTokenForOmråde {
    override fun kanBehandleKode6(): Boolean = ikkeImplementert("kanBehandleKode6")
    override fun kanBehandleKode7(): Boolean = ikkeImplementert("kanBehandleKode7")
    override fun kanBehandleEgneAnsatte(): Boolean = ikkeImplementert("kanBehandleEgneAnsatte")
    override fun erOppgavebehandler(): Boolean = ikkeImplementert("erOppgavebehandler")
    override fun erSaksbehandler(): Boolean = ikkeImplementert("erSaksbehandler")
    override fun erDrifter(): Boolean = ikkeImplementert("erDrifter")
    override fun erVeileder(): Boolean = ikkeImplementert("erVeileder")
    override fun harBasistilgang(): Boolean = ikkeImplementert("harBasistilgang")

    private fun ikkeImplementert(metode: String): Nothing =
        throw NotImplementedError("Gruppetilganger for område UNG er ikke implementert ennå ($metode)")
}
