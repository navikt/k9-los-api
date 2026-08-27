package no.nav.k9.los.infrastruktur.abac.tilganger

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.infrastruktur.rest.NavHeaders
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.infrastruktur.utils.CacheObject
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * Henter innlogget ansatts tilganger fra sif-abac-pdp i stedet for å tolke
 * gruppe-claims på tokenet lokalt. PDP-en leser selv ident og grupper fra
 * OBO-tokenet, så vi videresender brukerens token (vekslet on-behalf-of).
 *
 * Dette kalles for hvert API-kall, og er derfor cachet per (navIdent, område).
 * Feil fra PDP cacher vi ikke — ved nedetid feiler kallet (fail-closed).
 */
class SifAbacPdpTilgangerKlient(
    configuration: Configuration,
    accessTokenClient: AccessTokenClient,
    scope: String,
    private val httpClient: HttpClient,
) {
    private val log: Logger = LoggerFactory.getLogger(SifAbacPdpTilgangerKlient::class.java)
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.sifAbacPdpUrl()
    private val scopes = setOf(scope)
    private val cache = Cache<TilgangCacheKey, OmrådeTilganger>(cacheSizeLimit = 1000)
    private val cacheVarighet: Duration = Duration.ofMinutes(10)

    suspend fun tilganger(område: Områder, idToken: IdToken): OmrådeTilganger {
        val nøkkel = TilgangCacheKey(navIdent = idToken.getNavIdent(), område = område)
        val nå = LocalDateTime.now()
        cache.get(nøkkel, nå)?.let { return it.value }

        val tilganger = hentFraPdp(område, idToken)
        cache.set(nøkkel, CacheObject(tilganger, nå.plus(cacheVarighet)))
        return tilganger
    }

    private suspend fun hentFraPdp(område: Områder, idToken: IdToken): OmrådeTilganger {
        val oboToken = cachedAccessTokenClient.getOnBehalfOfAccessToken(scopes, idToken.value)
        val path = when (område) {
            Områder.K9 -> "k9"
            Områder.AKTIVITETSPENGER -> "ung"
        }
        val response = Retry.retry(
            tries = 3,
            operation = "innlogget-ansatt-tilganger-$path",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log,
        ) {
            httpClient.get("${url}/api/$path/nav-ansatt/v2") {
                header(HttpHeaders.Authorization, oboToken.asAuthoriationHeader())
                header(HttpHeaders.Accept, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }.also { r ->
                // Retry utløses kun ved exception — kast ved feilstatus for å faktisk få retry.
                if (!r.status.isSuccess()) {
                    throw IllegalStateException("Feil ved henting av tilganger for innlogget ansatt fra sif-abac-pdp: HTTP ${r.status.value} ${r.status.description}")
                }
            }
        }

        val body = response.bodyAsText()
        return when (område) {
            Områder.K9 -> OmrådeTilganger.fraK9(LosObjectMapper.instance.readValue<InnloggetAnsattK9V2Dto>(body))
            Områder.AKTIVITETSPENGER -> OmrådeTilganger.fraUng(LosObjectMapper.instance.readValue<InnloggetAnsattUngV2Dto>(body))
        }
    }
}
