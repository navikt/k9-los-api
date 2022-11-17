package no.nav.k9.los.integrasjon.omsorgspenger

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import com.google.gson.GsonBuilder
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.aksjonspunktbehandling.objectMapper
import no.nav.k9.los.integrasjon.rest.NavHeaders
import no.nav.k9.los.integrasjon.rest.idToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

private val gson = GsonBuilder().setPrettyPrinting().create()

open class OmsorgspengerService constructor(
    val configuration: Configuration,
    val accessTokenClient: AccessTokenClient

) : IOmsorgspengerService {
    private val log: Logger = LoggerFactory.getLogger(OmsorgspengerService::class.java)

    private val url = configuration.omsorgspengerUrl()

    private val scope = configuration.omsorgspengerSakScope()
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)

    private val NOT_FOUND = 404

    override suspend fun hentOmsorgspengerSakDto(sakFnrDto: OmsorgspengerSakFnrDto): OmsorgspengerSakDto? {
        val bodyRequest = gson.toJson(sakFnrDto)

        val httpRequest = "${url}/saksnummer"
            .httpPost()
            .body(
                bodyRequest
            )
            .header(
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(
                    setOf(scope),
                    kotlin.coroutines.coroutineContext.idToken().value
                ).asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )

        val json = Retry.retry(
            operation = "hent-saksnummer-omsorgspenger",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            val (request, _, result) = Operation.monitored(
                app = "k9-los-api",
                operation = "hent-saksnummer-omsorgspenger",
                resultResolver = { 200 == it.second.statusCode }
            ) { httpRequest.awaitStringResponseResult() }

            result.fold(
                { success ->
                    success
                },
                { error ->
                    val response = error.response
                    val statusCode = response.statusCode
                    // omsorgspenger gir 404 når de ikke finner sak på fnr...
                    if (statusCode != NOT_FOUND) {
                        log.error(
                            "Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'"
                        )
                        log.error(error.toString())
                    }
                    null
                }
            )
        }
        return try {
            if (!json.isNullOrEmpty()) {
                return objectMapper().readValue(json)
            }
            return null
        } catch (e: Exception) {
            log.warn("", e)
            null
        }
    }
}


