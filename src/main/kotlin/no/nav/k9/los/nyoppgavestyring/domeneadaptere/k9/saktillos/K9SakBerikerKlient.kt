package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.NavHeaders
import no.nav.k9.sak.kontrakt.behandling.BehandlingDto
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

class K9SakBerikerKlient(
    private val configuration: Configuration,
    private val accessTokenClient: AccessTokenClient
) {
    val log = LoggerFactory.getLogger("K9SakAdapter")
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.k9Url()

    fun hentBehandling(behandlingUUID: UUID): BehandlingDto {
        var behandlingDto: BehandlingDto? = null
        runBlocking {
            launch {
                behandlingDto = hent(behandlingUUID)
            }
        }
        return behandlingDto!!
    }

    suspend fun hent(behandlingUUID: UUID): BehandlingDto {
        val body = jacksonObjectMapper().writeValueAsString("behandlingsId?")
        val parameters = listOf<Pair<String, String>>(Pair("behandlingUuid", behandlingUUID.toString()))
        val httpRequest = "${url}/behandling"
            .httpGet(parameters)
            .header(
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(emptySet()).asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )

        val (_,_, result) = httpRequest.awaitStringResponseResult()
        val abc = result.fold(
            { success ->
                success
            },
            { error ->
                log.error(
                    "Error response = '${error.response.body().asString("text/plain")}' fra '${httpRequest.url}'"
                )
                log.error(error.toString())
            }
        )

       /* val json = Retry.retry(
            operation = "hent behandling",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            val (request, _, result) = Operation.monitored(
                app = "k9-los-api",
                operation = "hent-behandling",
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
        }*/
        return jacksonObjectMapper().readValue(abc.toString(), BehandlingDto::class.java)
    }
}