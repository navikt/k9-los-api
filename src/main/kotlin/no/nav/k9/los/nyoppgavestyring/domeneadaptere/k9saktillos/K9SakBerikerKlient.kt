package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import org.slf4j.LoggerFactory
import io.ktor.http.*
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.k9.los.integrasjon.rest.NavHeaders
import java.util.*
import java.time.Duration
import no.nav.helse.dusseldorf.ktor.metrics.Operation

class K9SakBerikerKlient(
    private val configuration: Configuration,
    private val accessTokenClient: AccessTokenClient
) {
    val log = LoggerFactory.getLogger("K9SakAdapter")
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.k9Url()

    suspend fun hentVedtaksinfo(): Any {
        val body = jacksonObjectMapper().writeValueAsString("behandlingsId?")
        val httpRequest = "${url}/behandling/backend-root/vedtak"
            .httpPost()
            .body(
                body
            )
            .header(
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(emptySet()).asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )

        val json = Retry.retry(
            operation = "hent vedtaksinfo",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            val (request, _, result) = Operation.monitored(
                app = "k9-los-api",
                operation = "hent-vedtak",
                resultResolver = { 200 == it.second.statusCode }
            ) { httpRequest.awaitStringResponseResult() }

            result.fold(
                { success ->
                    success
                },
                { error ->
                    log.error(
                        "Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'"
                    )
                    log.error(error.toString())
                }
            )
        }
        return json
    }
}