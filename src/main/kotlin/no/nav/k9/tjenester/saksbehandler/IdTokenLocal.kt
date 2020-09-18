package no.nav.k9.tjenester.saksbehandler

data class IdTokenLocal(
    override val value: String = "",

) : IIdToken {
    override val jwt = null
    override fun getName(): String = "saksbehandler@nav.no"
    override fun getUsername(): String = "saksbehandler@nav.no"
    override fun kanBehandleKode6(): Boolean = true
    override fun kanBehandleKode7(): Boolean = true
    override fun kanBehandleEgneAnsatte(): Boolean = true
    override fun erOppgavebehandler(): Boolean = true
}