package no.nav.k9.los.tjenester.saksbehandler

interface IIdToken {
    val value: String
    val jwt: JWTToken?
    fun getNavIdent(): String
    fun getName(): String
    fun getUsername(): String
    fun kanBehandleKode6(): Boolean
    fun kanBehandleKode7(): Boolean
    fun kanBehandleEgneAnsatte(): Boolean
    fun erOppgavebehandler(): Boolean
    fun erSaksbehandler() : Boolean
    fun erDrifter() : Boolean
    fun erVeileder() : Boolean
    fun harBasistilgang() : Boolean

}