package no.nav.k9.los

import no.nav.helse.dusseldorf.ktor.auth.Client
import no.nav.helse.dusseldorf.ktor.auth.PrivateKeyClient
import no.nav.helse.dusseldorf.oauth2.client.DirectKeyId
import no.nav.helse.dusseldorf.oauth2.client.FromJwk
import no.nav.helse.dusseldorf.oauth2.client.SignedJwtAccessTokenClient
import no.nav.k9.los.Configuration.Companion.AZURE_V2_ALIAS

class AccessTokenClientResolver(
    private val clients: Map<String, Client>) {

    private val azureV2 = azureV2Client().let {
        SignedJwtAccessTokenClient(
            clientId = it.clientId(),
            tokenEndpoint = it.tokenEndpoint(),
            privateKeyProvider = FromJwk(it.privateKeyJwk),
            keyIdProvider = DirectKeyId(it.certificateHexThumbprint)
        )
    }

    private fun azureV2Client() : PrivateKeyClient {
        val client = clients.getOrElse(AZURE_V2_ALIAS) {
            throw IllegalStateException("Client[$AZURE_V2_ALIAS] må være satt opp.")
        }
        return client as PrivateKeyClient
    }

    internal fun azureV2() = azureV2
}