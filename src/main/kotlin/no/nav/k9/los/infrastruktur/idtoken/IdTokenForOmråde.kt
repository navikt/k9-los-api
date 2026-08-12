package no.nav.k9.los.infrastruktur.idtoken

import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

/**
 * Et ekte (parset) token, tolket for ett bestemt [område].
 *
 * Implementasjonene deler alt som ikke er område-spesifikt: selve JWT-en og identiteten til
 * innlogget bruker. Det eneste som skiller dem er hvordan gruppene i tokenet oversettes til
 * roller, siden K9 og UNG har hver sine AD-grupper.
 *
 * [forOmråde] sender den ferdig parsede [jwt] videre til implementasjonen for det andre området,
 * slik at et token bare tolkes én gang uansett hvor mange områder det spørres om.
 */
sealed interface IdTokenForOmråde : IIdToken {
    /** Området dette tokenet er tolket for. */
    val område: Områder

    /** Ekte tokens har alltid en parset JWT, i motsetning til [IdTokenLocal]. */
    override val jwt: JWTToken

    override fun getNavIdent(): String = jwt.NAVident
    override fun getName(): String = jwt.name
    override fun getUsername(): String = jwt.preferredUsername

    override fun forOmråde(område: Områder): IIdToken =
        if (område == this.område) this
        else when (område) {
            Områder.K9 -> IdTokenK9(value, jwt)
            Områder.UNG -> IdTokenUng(value, jwt)
        }
}

