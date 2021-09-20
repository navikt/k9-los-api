package no.nav.k9

import no.nav.helse.dusseldorf.ktor.auth.Client
import no.nav.helse.dusseldorf.ktor.auth.ClientSecretClient
import no.nav.helse.dusseldorf.ktor.auth.PrivateKeyClient
import no.nav.helse.dusseldorf.oauth2.client.DirectKeyId
import no.nav.helse.dusseldorf.oauth2.client.FromJwk
import no.nav.helse.dusseldorf.oauth2.client.SignedJwtAccessTokenClient
import no.nav.k9.Configuration.Companion.AZURE_V2_ALIAS
import no.nav.k9.Configuration.Companion.NAIS_STS_ALIAS

class AccessTokenClientResolver(
    private val clients: Map<String, Client>) {

    private val naisSts = naisStsClient().let {
        NaisStsAccessTokenClient(
            clientId = it.clientId(),
            clientSecret = it.clientSecret,
            tokenEndpoint = it.tokenEndpoint()
        )
    }
    private val azureV2 = azureV2Client().let {
        SignedJwtAccessTokenClient(
            clientId = it.clientId(),
            tokenEndpoint = it.tokenEndpoint(),
            privateKeyProvider = FromJwk(it.privateKeyJwk),
            keyIdProvider = DirectKeyId(it.certificateHexThumbprint)
        )
    }

    private fun naisStsClient() : ClientSecretClient {
        val client = clients.getOrElse(NAIS_STS_ALIAS) {
            throw IllegalStateException("Client[$NAIS_STS_ALIAS] må være satt opp.")
        }
        return client as ClientSecretClient
    }

    private fun azureV2Client() : PrivateKeyClient {
        val client = clients.getOrElse(AZURE_V2_ALIAS) {
            throw IllegalStateException("Client[$AZURE_V2_ALIAS] må være satt opp.")
        }
        return client as PrivateKeyClient
    }

    internal fun naisSts() = naisSts
    internal fun azureV2() = azureV2
}