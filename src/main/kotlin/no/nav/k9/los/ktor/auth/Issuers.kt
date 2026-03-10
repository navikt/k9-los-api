package no.nav.k9.los.ktor.auth

import com.auth0.jwk.JwkProvider
import java.net.URI

open class Issuer(
        private val issuer: String,
        private val jwksUri: URI = URI("unused"),
        private val audience: String? = null,
        private val alias: String,
        private val jwkProvider: JwkProvider? = null) {
    fun issuer() : String = issuer
    fun jwksUri() : URI = jwksUri
    fun alias() = alias
    fun jwkProvider() : JwkProvider? = jwkProvider

    open fun asClaimRules() : MutableSet<ClaimRule> {
        val claimRules = mutableSetOf<ClaimRule>()
        if (audience != null) claimRules.add(StandardClaimRules.Companion.EnforceAudienceEquals(audience))
        return claimRules
    }
}

data class Azure(
        private val issuer: String,
        private val jwksUri: URI,
        private val audience: String,
        private val alias: String,
        private val authorizedClients: Set<String>,
        private val requiredGroups: Set<String>,
        private val requiredRoles: Set<String>,
        private val requiredScopes: Set<String>,
        private val requireCertificateClientAuthentication: Boolean
) : Issuer(issuer, jwksUri, audience, alias) {
    override fun asClaimRules() : MutableSet<ClaimRule> {
        val claimRules = super.asClaimRules()
        if (requireCertificateClientAuthentication) claimRules.add(AzureClaimRules.Companion.EnforceCertificateClientAuthentication())
        if (authorizedClients.isNotEmpty()) claimRules.add(AzureClaimRules.Companion.EnforceAuthorizedClient(authorizedClients))
        if (requiredGroups.isNotEmpty()) claimRules.add(AzureClaimRules.Companion.EnforceInAllGroups(requiredGroups))
        if (requiredRoles.isNotEmpty()) claimRules.add(AzureClaimRules.Companion.EnforceHasAllRoles(requiredRoles))
        if (requiredScopes.isNotEmpty()) claimRules.add(AzureClaimRules.Companion.EnforceHasAllScopes(requiredScopes))
        return claimRules
    }
}
