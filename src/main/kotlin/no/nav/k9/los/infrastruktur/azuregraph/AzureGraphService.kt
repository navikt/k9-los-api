package no.nav.k9.los.infrastruktur.azuregraph

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessToken
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

open class AzureGraphService(
    accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient
) : IAzureGraphService {
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val saksbehandlerUserIdCache = Cache<String, UUID>(cacheSizeLimit = 1000)
    private val saksbehandlerGrupperCache = Cache<String, Set<UUID>>(cacheSizeLimit = 1000)
    private val log = LoggerFactory.getLogger("AzureGraphService")!!

    private suspend fun håndterResultat(
        response: HttpResponse
    ): String {
        if (response.status.isSuccess()) {
            return response.bodyAsText()
        } else {
            log.error("HTTP ${response.status.value} ${response.status.description}")
            throw IllegalStateException("Feil ved henting av saksbehandlers id")
        }
    }

    override suspend fun hentEnhet(brukerkontekst: BrukerkontekstMedOmråde): String {
        return hentEnhet(brukerkontekst.navIdent, brukerkontekst.idToken)
    }

    override suspend fun hentEnhet(navIdent: String, idToken: IIdToken): String {
        require(navIdent == idToken.getNavIdent()) { "Token gjelder ikke valgt saksbehandler" }
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
        return LosObjectMapper.instance.readValue<OfficeLocation>(json).officeLocation
            ?: throw IllegalStateException("Microsoft Graph returnerte ikke enhet for innlogget saksbehandler")
    }

    override suspend fun hentGrupper(navIdent: String): Set<UUID> {
        val userId = hentUserIdForSaksbehandler(navIdent)
        return hentGrupperForSaksbehandler(userId, navIdent)
    }

    override suspend fun hentGrupper(brukerkontekst: BrukerkontekstMedOmråde): Set<UUID> {
        val token = brukerkontekst.idToken
        require(brukerkontekst.navIdent == token.getNavIdent()) { "Token gjelder ikke valgt saksbehandler" }
        return saksbehandlerGrupperCache.hentSuspend(brukerkontekst.navIdent) {
            val accessToken = accessToken(token)
            val json = Retry.retry(
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
            LosObjectMapper.instance.readValue<DirectoryOjects>(json).value.map { it.id }.toSet()
        }
    }

    private suspend fun hentUserIdForSaksbehandler(saksbehandlerIdent: String): UUID {
        return saksbehandlerUserIdCache.hentSuspend(saksbehandlerIdent) {
            val accessToken = accessToken(null)
            val json = Retry.retry(
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
                        parameter($$"$filter", "onPremisesSamAccountName eq '${saksbehandlerIdent.replace("'", "''")}'")
                        parameter($$"$count", "true")
                        parameter($$"$select", "id")
                        header(HttpHeaders.Accept, "application/json")
                        header(HttpHeaders.Authorization, "Bearer ${accessToken.token}")
                        header("ConsistencyLevel", "eventual")
                    }
                }
                håndterResultat(response)
            }

            val (value) = LosObjectMapper.instance.readValue<UserIdFilterResult>(json)
            if (value.size != 1) {
                throw IllegalArgumentException("Fikk ${value.size} treff på saksbehandler i microsoft graph, forventet 1 treff")
            }
            value.first().id
        }
    }

    private suspend fun hentGrupperForSaksbehandler(saksbehandlerUserId: UUID, saksbehandlerIdent: String): Set<UUID> {
        return saksbehandlerGrupperCache.hentSuspend(saksbehandlerIdent) {
            val accessToken = accessToken(null)
            val json = Retry.retry(
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
            LosObjectMapper.instance.readValue<DirectoryOjects>(json).value.map { it.id }.toSet()
        }
    }


    private fun accessToken(onBehalfOf: IIdToken? = null): AccessToken {
        return onBehalfOf?.run {
            cachedAccessTokenClient.getOnBehalfOfAccessToken(setOf("https://graph.microsoft.com/user.read"), this.value)
        } ?: cachedAccessTokenClient.getClientCredentialsAccessToken(setOf("https://graph.microsoft.com/.default"))
    }
}