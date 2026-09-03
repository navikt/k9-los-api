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
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.rest.NavHeaders
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import org.slf4j.LoggerFactory
import java.util.*

internal class SifAbacPdpTilgangerKlient(
    configuration: Configuration,
    accessTokenClient: AccessTokenClient,
    scope: String,
    private val httpClient: HttpClient,
) : TilgangerKlient {
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.sifAbacPdpUrl().trimEnd('/')
    private val scopes = setOf(scope)

    override suspend fun hent(idToken: IIdToken): PdpResultat {
        val oboToken = cachedAccessTokenClient.getOnBehalfOfAccessToken(scopes, idToken.value)
        val response = Retry.retry(
            tries = 2,
            operation = "hente tilganger fra sif-abac-pdp",
            logger = log,
        ) {
            httpClient.get("$url/api/k9/nav-ansatt/v2") {
                header(HttpHeaders.Authorization, oboToken.asAuthoriationHeader())
                header(HttpHeaders.Accept, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }
        }

        if (!response.status.isSuccess()) {
            return PdpResultat.Feil("HTTP", response.status.value)
        }

        val dto = LosObjectMapper.instance.readValue<InnloggetAnsattK9V2Dto>(response.bodyAsText())
        return PdpResultat.Suksess(dto.tilganger())
    }

    private companion object {
        val log = LoggerFactory.getLogger(SifAbacPdpTilgangerKlient::class.java)
    }
}
