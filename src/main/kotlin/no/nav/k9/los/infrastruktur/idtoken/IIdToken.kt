package no.nav.k9.los.infrastruktur.idtoken

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

/**
 * Innlogget brukers token.
 *
 * Rollemetodene er område-spesifikke: hvilke AD-grupper som gir en rolle avhenger av området
 * (K9 og UNG har hver sine grupper). Kall [forOmråde] for å få tokenet tolket i kontekst av ett
 * område, f.eks. `idToken.forOmråde(Områder.K9).erSaksbehandler()`.
 *
 * Rutingen ligger her i stedet for i en egen ruterklasse, slik at tokenet i request-konteksten
 * (se CoroutineRequestContext) er det eneste kallere trenger å forholde seg til.
 */
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

    /**
     * Tokenet tolket for [område]. Se [IdTokenForOmråde] for rutingen mellom områdene.
     */
    fun forOmråde(område: Områder): IIdToken
}