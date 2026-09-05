package no.nav.k9.los.infrastruktur.azuregraph

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessToken
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import org.slf4j.LoggerFactory
import java.time.Duration

open class AzureGraphService(
    accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient
) : IAzureGraphService {
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val log = LoggerFactory.getLogger("AzureGraphService")!!

    private suspend fun håndterResultat(
        response: HttpResponse
    ): String {
        if (response.status.isSuccess()) {
            return response.bodyAsText()
        } else {
            log.error(
                "Error response = '${response.bodyAsText()}' fra '${response.request.url}'"
            )
            log.error("HTTP ${response.status.value} ${response.status.description}")
            throw IllegalStateException("Feil ved henting av saksbehandlers id")
        }
    }

    override suspend fun hentEnhet(navIdent: String, idToken: IdToken): String {
        val accessToken = accessToken(idToken)

        val json = Retry.retry(
            operation = "office-location",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            val response = Operation.monitored(
                app = "k9-los-api",
                operation = "office-location",
                resultResolver = { 200 == it.status.value }
            ) {
                httpClient.get {
                    url("https://graph.microsoft.com/v1.0/me")
                    parameter($$"$select", "officeLocation")
                    header(HttpHeaders.Accept, "application/json")
                    header(HttpHeaders.Authorization, "Bearer ${accessToken.token}")
                    header("ConsistencyLevel", "eventual")
                }
            }

            håndterResultat(response)
        }
        return LosObjectMapper.instance.readValue<OfficeLocation>(json).officeLocation ?: ""
    }

    private fun accessToken(idToken: IdToken): AccessToken {
        return cachedAccessTokenClient.getOnBehalfOfAccessToken(
            setOf("https://graph.microsoft.com/user.read"),
            idToken.value
        )
    }
}
