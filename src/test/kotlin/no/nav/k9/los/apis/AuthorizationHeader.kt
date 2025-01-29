package no.nav.k9.los.apis

import no.nav.helse.dusseldorf.testsupport.jws.Azure
import java.util.*

internal fun authorizationHeader(
    clientId: String = "k9-los-oidc-auth-proxy",
    audience: String = "k9-los-api",
    username: String = "Saksbehandler",
    navIdent : String = "Z000000",
    clientAuthenticationMode: Azure.ClientAuthenticationMode = Azure.ClientAuthenticationMode.CERTIFICATE) = "${UUID.randomUUID()}".let { uuid -> Azure.V2_0.generateJwt(
    clientId = clientId,
    audience = audience,
    accessAsApplication = false,
    clientAuthenticationMode = clientAuthenticationMode,
    overridingClaims = mapOf(
        "aio" to uuid,
        "preferred_username" to username,
        "name" to "$username Nordmann",
        "groups" to listOf(uuid),
        "tid" to uuid,
        "uti" to uuid,
        "oid" to uuid,
        "NAVident" to navIdent,
    )
)}.let { "Bearer $it" }