package no.nav.k9.integrasjon.azuregraph

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.integrasjon.rest.idToken
import no.nav.k9.tjenester.saksbehandler.IdToken
import no.nav.k9.utils.Cache
import no.nav.k9.utils.CacheObject
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime

open class AzureGraphService constructor(
    accessTokenClient: AccessTokenClient
) : IAzureGraphService {
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val cache = Cache<String>()
    val log = LoggerFactory.getLogger("AzureGraphService")

    override suspend fun hentIdentTilInnloggetBruker(): String {
        val username = IdToken(kotlin.coroutines.coroutineContext.idToken().value).getUsername()
        val cachedObject = cache.get(username)
        if (cachedObject == null) {
            val accessToken =
                cachedAccessTokenClient.getAccessToken(
                    setOf("https://graph.microsoft.com/user.read"),
                    kotlin.coroutines.coroutineContext.idToken().value
                )

            val httpRequest = "https://graph.microsoft.com/v1.0/me?\$select=onPremisesSamAccountName"
                .httpGet()
                .header(
                    HttpHeaders.Accept to "application/json",
                    HttpHeaders.Authorization to "Bearer ${accessToken.token}"
                )


            val json = Retry.retry(
                operation = "hent-ident",
                initialDelay = Duration.ofMillis(200),
                factor = 2.0,
                logger = log
            ) {
                val (request, _, result) = Operation.monitored(
                    app = "k9-los-api",
                    operation = "hent-ident",
                    resultResolver = { 200 == it.second.statusCode }
                ) { httpRequest.awaitStringResponseResult() }

                result.fold(
                    { success -> success },
                    { error ->
                        log.error(
                            "Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'"
                        )
                        log.error(error.toString())
                        throw IllegalStateException("Feil ved henting av saksbehandlers id")
                    }
                )
            }
            return try {
                val onPremisesSamAccountName = objectMapper().readValue<AccountName>(json).onPremisesSamAccountName
                cache.set(username, CacheObject(onPremisesSamAccountName, LocalDateTime.now().plusDays(180)))
                return onPremisesSamAccountName
            } catch (e: Exception) {
                log.error(
                    "Feilet deserialisering", e
                )
                ""
            }
        } else {
            return cachedObject.value
        }
    }

    override suspend fun hentEnhetForInnloggetBruker(): String {
      
        val username = IdToken(kotlin.coroutines.coroutineContext.idToken().value).getUsername() + "_office_location"
        val cachedObject = cache.get(username)
        if (cachedObject == null) {
            val accessToken =
                cachedAccessTokenClient.getAccessToken(
                    setOf("https://graph.microsoft.com/user.read"),
                    kotlin.coroutines.coroutineContext.idToken().value
                )

            val httpRequest = "https://graph.microsoft.com/v1.0/me?\$select=officeLocation"
                .httpGet()
                .header(
                    HttpHeaders.Accept to "application/json",
                    HttpHeaders.Authorization to "Bearer ${accessToken.token}"
                )


            val json = Retry.retry(
                operation = "office-location",
                initialDelay = Duration.ofMillis(200),
                factor = 2.0,
                logger = log
            ) {
                val (request, _, result) = Operation.monitored(
                    app = "k9-los-api",
                    operation = "office-location",
                    resultResolver = { 200 == it.second.statusCode }
                ) { httpRequest.awaitStringResponseResult() }

                result.fold(
                    { success -> success },
                    { error ->
                        log.error(
                            "Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'"
                        )
                        log.error(error.toString())
                        throw IllegalStateException("Feil ved henting av saksbehandlers id")
                    }
                )
            }
            return try {
                val officeLocation = objectMapper().readValue<OfficeLocation>(json).officeLocation
                cache.set(username, CacheObject(officeLocation, LocalDateTime.now().plusDays(180)))
                return officeLocation
            } catch (e: Exception) {
                log.error(
                    "Feilet deserialisering", e
                )
                ""
            }
        } else {
            return cachedObject.value
        }
    }
    
}



