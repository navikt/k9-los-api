package no.nav.k9.los.infrastruktur.idtoken

data class IdTokenK9(
    override val value: String,
    override val jwt: JWTToken = JWTToken.fra(value),
) : IdTokenForOmråde {
    override fun kanBehandleKode6(): Boolean = iGruppe("BRUKER_GRUPPE_ID_KODE6")
    override fun kanBehandleKode7(): Boolean = iGruppe("BRUKER_GRUPPE_ID_KODE7")
    override fun kanBehandleEgneAnsatte(): Boolean = iGruppe("BRUKER_GRUPPE_ID_EGENANSATT")
    override fun erOppgavebehandler(): Boolean = iGruppe("BRUKER_GRUPPE_ID_OPPGAVESTYRER")
    override fun erSaksbehandler(): Boolean = iGruppe("BRUKER_GRUPPE_ID_SAKSBEHANDLER")
    override fun erVeileder(): Boolean = iGruppe("BRUKER_GRUPPE_ID_VEILEDER")
    override fun erDrifter(): Boolean = iGruppe("BRUKER_GRUPPE_ID_DRIFT")
    override fun harBasistilgang(): Boolean = erSaksbehandler() || erVeileder()

    private fun iGruppe(miljøvariabel: String): Boolean =
        jwt.groups.any { gruppe -> gruppe == System.getenv(miljøvariabel)!! }
}
