package no.nav.k9.los.integrasjon.azuregraph

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import io.ktor.http.HttpHeaders
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessToken
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.EnheterSomSkalUtelatesFraLos
import no.nav.k9.los.tjenester.saksbehandler.IIdToken
import no.nav.k9.los.tjenester.saksbehandler.IdToken
import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext

open class AzureGraphService constructor(
    accessTokenClient: AccessTokenClient
) : IAzureGraphService {
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val cache = Cache<String, String>()
    val log = LoggerFactory.getLogger("AzureGraphService")!!

    override suspend fun hentIdentTilInnloggetBruker(): String {
        val username = IdToken(coroutineContext.idToken().value).getUsername()
        val cachedObject = cache.get(username)
        if (cachedObject == null) {

            val httpRequest = "https://graph.microsoft.com/v1.0/me?\$select=onPremisesSamAccountName"
                .httpGet()
                .header(
                    HttpHeaders.Accept to "application/json",
                    HttpHeaders.Authorization to "Bearer ${accessToken(coroutineContext.idToken()).token}"
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

                håndterResultat(result, request)
            }
            return try {
                val onPremisesSamAccountName = LosObjectMapper.instance.readValue<AccountName>(json).onPremisesSamAccountName
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

    private fun håndterResultat(
        result: Result<String, FuelError>,
        request: Request
    ) = result.fold(
        { success -> success },
        { error ->
            log.error(
                "Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'"
            )
            log.error(error.toString())
            throw IllegalStateException("Feil ved henting av saksbehandlers id")
        }
    )

    override suspend fun hentEnhetForInnloggetBruker(): String {
        coroutineContext.idToken().let { brukersToken ->
            val brukernavnFraKontekst = IdToken(brukersToken.value).getUsername()
            return hentEnhetForBruker(brukernavn = brukernavnFraKontekst, onBehalfOf = brukersToken)
        }
    }

    override suspend fun hentEnhetForBrukerMedSystemToken(brukernavn: String): String? {
        return try {
            hentEnhetForBruker(brukernavn = brukernavn)
                .takeIf { EnheterSomSkalUtelatesFraLos.sjekkKanBrukes(it) }
        } catch (e: Exception) {
            log.warn("Klarte ikke å hente behandlende enhet for $brukernavn", e)
            null
        }
    }

    private suspend fun hentEnhetForBruker(brukernavn: String, onBehalfOf: IIdToken? = null): String {
        val key = brukernavn + "_office_location"
        val cachedOfficeLocation = cache.get(key)
        if (cachedOfficeLocation == null) {
            val accessToken = accessToken(onBehalfOf)

            val graphUrl = if (onBehalfOf != null) {
                "https://graph.microsoft.com/v1.0/me?\$select=officeLocation"
            } else {
                "https://graph.microsoft.com/v1.0/users?\$filter=mailNickname eq '$brukernavn'&\$select=officeLocation"
            }

            val httpRequest = graphUrl
                .httpGet()
                .header(
                    HttpHeaders.Accept to "application/json",
                    HttpHeaders.Authorization to "Bearer ${accessToken.token}",
                    "ConsistencyLevel" to "eventual",
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

                håndterResultat(result, request)
            }
            return try {
                val officeLocation = if (onBehalfOf != null) {
                    LosObjectMapper.instance.readValue<OfficeLocation>(json).officeLocation
                } else {
                    val result = LosObjectMapper.instance.readValue<OfficeLocationFilterResult>(json).value.also {
                        if (it.size > 1) log.warn("Flere enn 1 treff på ident")
                    }
                    result.first().officeLocation
                }
                cache.set(key, CacheObject(officeLocation, LocalDateTime.now().plusDays(180)))
                return officeLocation
            } catch (e: Exception) {
                log.error(
                    "Feilet deserialisering", e
                )
                ""
            }
        } else {
            return cachedOfficeLocation.value
        }
    }

    private fun accessToken(onBehalfOf: IIdToken? = null): AccessToken {
        return onBehalfOf?.run {
            cachedAccessTokenClient.getAccessToken(setOf("https://graph.microsoft.com/user.read"), this.value)
        } ?: cachedAccessTokenClient.getAccessToken(setOf("https://graph.microsoft.com/.default"))
    }
}



