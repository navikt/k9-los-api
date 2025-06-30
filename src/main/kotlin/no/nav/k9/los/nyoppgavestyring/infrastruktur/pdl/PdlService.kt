package no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.NavHeaders
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.CacheObject
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.coroutineContext

class PdlService(
    baseUrl: URI,
    accessTokenClient: AccessTokenClient,
    scope: String,
    val azureGraphService : IAzureGraphService,
    private val httpClient: HttpClient
) : IPdlService {
    private val log: Logger = LoggerFactory.getLogger(PdlService::class.java)
    private val scopes = setOf(scope)

    private val personUrl = baseUrl.toString()

    private val graphqlQueryHentPerson = getStringFromResource("/pdl/hentPerson.graphql")
    private val graphqlQueryHentIdent = getStringFromResource("/pdl/hentIdent.graphql")

    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val pdlCacheVarighet = Duration.ofHours(7)
    private data class AktørIdTilPersonCacheKey(val saksbehandlerIdent: String, val aktørId: String)
    private val aktørIdTilPersonCache = Cache<AktørIdTilPersonCacheKey, PersonPdlResponse>(10_000)
    private data class FrnTilAktørIdCacheKey(val saksbehandlerIdent: String, val fnr: String)
    private val fnrTilAktørIdCache = Cache<FrnTilAktørIdCacheKey, PdlResponse>(10_000)

    override suspend fun person(aktorId: String): PersonPdlResponse {
        if (aktorId.isEmpty()) {
            log.info("Forsøker å hente person med tom aktorId")
            return PersonPdlResponse(false, null)
        }

        val queryRequest = QueryRequest(
            graphqlQueryHentPerson,
            mapOf("ident" to aktorId)
        )

        val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()
        val cacheKey = AktørIdTilPersonCacheKey(saksbehandlerIdent, aktorId)
        val cachedObject = aktørIdTilPersonCache.get(cacheKey)
        if (cachedObject != null) {
            return cachedObject.value
        }
        val callId = UUID.randomUUID().toString()
        val json = Retry.retry(
            operation = "hente-person",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            val response = Operation.monitored(
                app = "k9-los-api",
                operation = "hente-person",
                resultResolver = { 200 == it.status.value }
            ) {
                httpClient.post(personUrl) {
                    setBody(LosObjectMapper.instance.writeValueAsString(queryRequest))
                    header(HttpHeaders.Authorization, authorizationHeader())
                    header(HttpHeaders.Accept, "application/json")
                    header(HttpHeaders.ContentType, "application/json")
                    header(NavHeaders.Tema, "OMS")
                    header(NavHeaders.CallId, callId)
                    header(NavHeaders.Behandlingsnummer, Behandlingsnummer.entries.map { it.behandlingsnummer })
                }
            }

            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                log.warn("Error response = '${response.bodyAsText()}' fra '${response.request.url}'")
                log.warn("HTTP ${response.status.value} ${response.status.description} aktorId callId: ${callId} ${coroutineContext.idToken().getUsername()}")
                null
            }
        }
        try {
            val readValue = LosObjectMapper.instance.readValue<PersonPdl>(json!!)
            val resultat = PersonPdlResponse(false, readValue)
            val now = LocalDateTime.now()
            aktørIdTilPersonCache.removeExpiredObjects(now)
            aktørIdTilPersonCache.set(cacheKey, CacheObject(resultat, now.plus(pdlCacheVarighet)))
            return resultat
        } catch (e: Exception) {
            try {
                val value = LosObjectMapper.instance.readValue<Error>(json!!)

                if (value.errors.any { it.extensions?.code == "unauthorized" }) {
                    val resultat = PersonPdlResponse(true, null)
                    //TODO vurder å cache her også
                    //aktørIdTilPersonCache.set(cacheKey, CacheObject(resultat, LocalDateTime.now().plus(pdlCacheVarighet)))
                    return resultat
                } else {
                    log.warn("Fikk ukjent error fra pdl (noe annet enn 'unauthorized'): ${value.errors.joinToString(",")}", e)
                }
            } catch (e: Exception) {
                log.warn("Ukjent feil ved tolkning av error fra pdl", e)
            }
            return PersonPdlResponse(false, null)
        }
    }

    override suspend fun identifikator(fnummer: String): PdlResponse {
        val queryRequest = QueryRequest(
            graphqlQueryHentIdent,
            mapOf(
                "ident" to fnummer,
                "historikk" to false,
                "grupper" to listOf("AKTORID")
            )
        )

        val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()
        val cacheKey = FrnTilAktørIdCacheKey(saksbehandlerIdent, fnummer)
        val cachedObject = fnrTilAktørIdCache.get(cacheKey)
        if (cachedObject != null) {
            return cachedObject.value
        }

        val callId = UUID.randomUUID().toString()
        val json = Retry.retry(
            operation = "hente-ident",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            val response = Operation.monitored(
                app = "k9-los-api",
                operation = "hente-ident",
                resultResolver = { 200 == it.status.value }
            ) {
                httpClient.post(personUrl) {
                    setBody(LosObjectMapper.instance.writeValueAsString(queryRequest))
                    header(HttpHeaders.Authorization, authorizationHeader())
                    header(HttpHeaders.Accept, "application/json")
                    header(HttpHeaders.ContentType, "application/json")
                    header(NavHeaders.Tema, "OMS")
                    header(NavHeaders.CallId, callId)
                    header(NavHeaders.Behandlingsnummer, Behandlingsnummer.entries.map { it.behandlingsnummer })
                }
            }

            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                log.warn("Error response = '${response.bodyAsText()}' fra '${response.request.url}'")
                log.warn("HTTP ${response.status.value} ${response.status.description}")
                null
            }
        }
        try {
            val resultat = PdlResponse(false, LosObjectMapper.instance.readValue<AktøridPdl>(json!!))
            val now = LocalDateTime.now()
            fnrTilAktørIdCache.removeExpiredObjects(now)
            fnrTilAktørIdCache.set(cacheKey, CacheObject(resultat, now.plus(pdlCacheVarighet)))
            return resultat
        } catch (e: Exception) {
            try {
                val value = LosObjectMapper.instance.readValue<Error>(json!!)
                log.warn("Fikk pdl-feil ${value.errors.joinToString(",")}", e)

                if (value.errors.any { it.extensions?.code == "unauthorized" }) {
                    val resultat = PdlResponse(true, null)
                    //TODO vurder å cache her også
                    //fnrTilAktørIdCache.set(cacheKey, CacheObject(resultat, LocalDateTime.now().plus(pdlCacheVarighet)))
                    return resultat
                }
            } catch (e: Exception) {
                log.warn("", e)
            }
            return PdlResponse(false, null)
        }
    }

    data class QueryRequest(
        val query: String,
        val variables: Map<String, Any>,
        val operationName: String? = null
    ) {
        data class Variables(
            val variables: Map<String, Any>
        )
    }

    private suspend fun authorizationHeader() = cachedAccessTokenClient.getAccessToken(
        scopes = scopes,
        onBehalfOf = coroutineContext.idToken().value
    ).asAuthoriationHeader()

    private fun getStringFromResource(path: String) =
        PdlService::class.java.getResourceAsStream(path).bufferedReader().use { it.readText() }

}



