package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.saksbehandling.systemklienter

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.avstemming.Behandlingstilstand
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.TransientException
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.NavHeaders
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

class RestAvstemmingsklient(
    private val url: String,
    private val navn: String,
    accessTokenClient: AccessTokenClient,
    scope: String,
    private val httpClient: HttpClient,
) : Avstemmingsklient {
    val log: Logger = LoggerFactory.getLogger("${navn}Avstemmingsklient")

    private val scopes = setOf(scope)
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)

    override fun hentÅpneBehandlinger(): List<Behandlingstilstand> {
        return runBlocking {
            hent()
        }
    }

    private suspend fun hent(): List<Behandlingstilstand> {
        val response = Retry.retry(
            tries = 3,
            operation = "hentAvstemming",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.get("${url}/los/avstemming/behandlingstilstand-alle-ikke-avsluttede") {
                header(
                    //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                    HttpHeaders.Authorization, cachedAccessTokenClient.getAccessToken(scopes).asAuthoriationHeader()
                )
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }
        }

        if (response.status == HttpStatusCode.NoContent) {
            return emptyList()
        }

        val responseBody = if (response.status.isSuccess()) {
            response.bodyAsText()
        } else {
            if (response.status == HttpStatusCode.ServiceUnavailable
                || response.status == HttpStatusCode.GatewayTimeout
                || response.status == HttpStatusCode.RequestTimeout
            ) {
                throw TransientException(
                    "$navn er ikke tilgjengelig for avstemming, fikk http code ${response.status.value}",
                    Exception("HTTP error ${response.status.value}")
                )
            } else {
                val feiltekst = response.bodyAsText()

                log.error("Error response = '$feiltekst' fra '${response.request.url}'")
                log.error("HTTP ${response.status.value} ${response.status.description}")
                throw IllegalStateException("Feil ved henting av behandling fra $navn")
            }
        }

        return LosObjectMapper.instance.readValue(responseBody)
    }
}