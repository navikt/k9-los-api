package no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessToken
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.CacheObject
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.EnheterSomSkalUtelatesFraLos
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

open class AzureGraphService(
    accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient
) : IAzureGraphService {
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val officeLocationCache = Cache<String, String>(cacheSizeLimit = 1000)
    private val saksbehandlerUserIdCache = Cache<String, UUID>(cacheSizeLimit = 1000)
    private val saksbehandlerGrupperCache = Cache<String, Set<UUID>>(cacheSizeLimit = 1000)
    private val log = LoggerFactory.getLogger("AzureGraphService")!!

    override suspend fun hentIdentTilInnloggetBruker(): String {
        return coroutineContext.idToken().getNavIdent()
    }

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

    override suspend fun hentEnhetForInnloggetBruker(): String {
        val token = coroutineContext.idToken()
        return hentEnhetForBruker(brukernavn = token.getUsername(), onBehalfOf = token)
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
        val cachedOfficeLocation = officeLocationCache.get(key)
        if (cachedOfficeLocation == null) {
            val accessToken = accessToken(onBehalfOf)

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
                        if (onBehalfOf != null) {
                            url("https://graph.microsoft.com/v1.0/me")
                            parameter("\$select", "officeLocation")
                        } else {
                            url("https://graph.microsoft.com/v1.0/users")
                            parameter("\$filter", "mailNickname eq '$brukernavn'")
                            parameter("\$select", "officeLocation")
                        }
                        header(HttpHeaders.Accept, "application/json")
                        header(HttpHeaders.Authorization, "Bearer ${accessToken.token}")
                        header("ConsistencyLevel", "eventual")
                    }
                }

                håndterResultat(response)
            }
            return try {
                val officeLocation = if (onBehalfOf != null) {
                    LosObjectMapper.instance.readValue<OfficeLocation>(json).officeLocation
                } else {
                    val result = LosObjectMapper.instance.readValue<OfficeLocationFilterResult>(json).value.also {
                        if (it.size > 1) log.warn("Flere enn 1 treff på ident")
                    }
                    if (result.isEmpty()) {
                        log.warn("Fant ingen treff på enhet for saksbehandler $brukernavn, bruker tom streng som enhet")
                        ""
                    } else {
                        result.first().officeLocation
                    }
                }
                officeLocationCache.set(key, CacheObject(officeLocation, LocalDateTime.now().plusDays(180)))
                return officeLocation
            } catch (e: Exception) {
                log.warn("Feilet i oppslag av enhet for saksbehandler $brukernavn, bruker tom streng som enhet", e)
                ""
            }
        } else {
            return cachedOfficeLocation.value
        }
    }

    override suspend fun hentGrupperForSaksbehandler(saksbehandlerIdent: String): Set<UUID> {
        val userId = hentUserIdForSaksbehandler(saksbehandlerIdent)
        return hentGrupperForSaksbehandler(userId, saksbehandlerIdent)
    }

    override suspend fun hentGrupperForInnloggetSaksbehandler(): Set<UUID> {
        val token = coroutineContext.idToken()
        return saksbehandlerGrupperCache.hent(coroutineContext.idToken().getNavIdent()) {
            val accessToken = accessToken(token)
            val json = runBlocking {
                Retry.retry(
                    operation = "grupper-for-saksbehandler",
                    initialDelay = Duration.ofMillis(200),
                    factor = 2.0,
                    logger = log
                ) {
                    val response = Operation.monitored(
                        app = "k9-los-api",
                        operation = "grupper-for-saksbehandler",
                        resultResolver = { 200 == it.status.value }
                    ) {
                        httpClient.get("https://graph.microsoft.com/v1.0/me/memberOf") {
                            header(HttpHeaders.Accept, "application/json")
                            header(HttpHeaders.Authorization, "Bearer ${accessToken.token}")
                            header("ConsistencyLevel", "eventual")
                        }
                    }
                    håndterResultat(response)
                }
            }
            LosObjectMapper.instance.readValue<DirectoryOjects>(json).value.map { it.id }.toSet()
        }

    }

    private fun hentUserIdForSaksbehandler(saksbehandlerIdent: String): UUID {
        return saksbehandlerUserIdCache.hent(saksbehandlerIdent) {
            val accessToken = accessToken(null)
            val json = runBlocking {
                Retry.retry(
                    operation = "user-id-for-saksbehandler",
                    initialDelay = Duration.ofMillis(200),
                    factor = 2.0,
                    logger = log
                ) {
                    val response = Operation.monitored(
                        app = "k9-los-api",
                        operation = "user-id-for-saksbehandler",
                        resultResolver = { 200 == it.status.value }
                    ) {
                        httpClient.get {
                            url("https://graph.microsoft.com/v1.0/users")
                            parameter("\$filter", "onPremisesSamAccountName eq '$saksbehandlerIdent'")
                            parameter("\$count", "true")
                            parameter("\$select", "id")
                            header(HttpHeaders.Accept, "application/json")
                            header(HttpHeaders.Authorization, "Bearer ${accessToken.token}")
                            header("ConsistencyLevel", "eventual")
                        }
                    }
                    håndterResultat(response)
                }
            }

            val (value) = LosObjectMapper.instance.readValue<UserIdFilterResult>(json)
            if (value.size != 1) {
                throw IllegalArgumentException("Fikk ${value.size} treff på saksbehandler i microsoft graph, forventet 1 treff")
            }
            value.first().id
        }
    }

    private fun hentGrupperForSaksbehandler(saksbehandlerUserId: UUID, saksbehandlerIdent: String): Set<UUID> {
        return saksbehandlerGrupperCache.hent(saksbehandlerIdent) {
            val accessToken = accessToken(null)
            val json = runBlocking {
                Retry.retry(
                    operation = "grupper-for-saksbehandler",
                    initialDelay = Duration.ofMillis(200),
                    factor = 2.0,
                    logger = log
                ) {
                    val response = Operation.monitored(
                        app = "k9-los-api",
                        operation = "grupper-for-saksbehandler",
                        resultResolver = { 200 == it.status.value }
                    ) {
                        httpClient.get("https://graph.microsoft.com/v1.0/users/$saksbehandlerUserId/memberOf") {
                            header(HttpHeaders.Accept, "application/json")
                            header(HttpHeaders.Authorization, "Bearer ${accessToken.token}")
                            header("ConsistencyLevel", "eventual")
                        }
                    }
                    håndterResultat(response)
                }
            }
            LosObjectMapper.instance.readValue<DirectoryOjects>(json).value.map { it.id }.toSet()
        }
    }


    private fun accessToken(onBehalfOf: IIdToken? = null): AccessToken {
        return onBehalfOf?.run {
            cachedAccessTokenClient.getAccessToken(setOf("https://graph.microsoft.com/user.read"), this.value)
        } ?: cachedAccessTokenClient.getAccessToken(setOf("https://graph.microsoft.com/.default"))
    }
}



