package no.nav.k9.los.infrastruktur.idtoken

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

data class IdTokenLocal(
    override val value: String = "",

) : IIdToken {
    override val jwt: Nothing? = null
    override fun getNavIdent(): String = "Z123456"
    override fun getName(): String = "saksbehandler@nav.no"
    override fun getUsername(): String = "saksbehandler@nav.no"
    override fun kanBehandleKode6(): Boolean = true
    override fun kanBehandleKode7(): Boolean = true
    override fun kanBehandleEgneAnsatte(): Boolean = true
    override fun erOppgavebehandler(): Boolean = true
    override fun erSaksbehandler(): Boolean = true
    override fun erVeileder(): Boolean = true
    override fun erDrifter(): Boolean = true
    override fun harBasistilgang(): Boolean = true

    // Lokalt kjøres alt uten faktiske grupper, så samme token brukes for alle områder
    override fun forOmråde(område: Områder): IIdToken = this
}
