package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.beriker

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.TransientException
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.NavHeaders
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

class K9SakSystemKlient(
    private val configuration: Configuration,
    accessTokenClient: AccessTokenClient,
    scope: String,
    private val httpClient: HttpClient
) : K9SakSystemKlientInterfaceKludge {
    val log: Logger = LoggerFactory.getLogger("K9SakAdapter")
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.k9Url()
    private val scopes = setOf(scope)

    @WithSpan
    override fun hentBehandling(behandlingUUID: UUID, antallForsøk: Int): BehandlingMedFagsakDto? {
        return runBlocking { hent(behandlingUUID, antallForsøk) }
    }

    private suspend fun hent(behandlingUUID: UUID, antallForsøk: Int = 3): BehandlingMedFagsakDto? {
        val response = Retry.retry(
            tries = antallForsøk,
            operation = "berik",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.get("${url}/los/behandling") {
                parameter("behandlingUuid", behandlingUUID.toString())
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
            return null
        }

        val abc = if (response.status.isSuccess()) {
            response.bodyAsText()
        } else {
            if (response.status == HttpStatusCode.ServiceUnavailable
                || response.status == HttpStatusCode.GatewayTimeout
                || response.status == HttpStatusCode.RequestTimeout
            ) {
                throw TransientException(
                    "k9sak er ikke tilgjengelig for beriking av k9sak-oppgave, fikk http code ${response.status.value}",
                    Exception("HTTP error ${response.status.value}")
                )
            }

            val feiltekst = response.bodyAsText()
            val ignorerManglendeTilgangPgaUtdatertTestdata = configuration.koinProfile == KoinProfile.PREPROD
                    && feiltekst.contains("MANGLER_TILGANG_FEIL")

            log.error(
                (if (ignorerManglendeTilgangPgaUtdatertTestdata) "IGNORERER I DEV: " else "") +
                        "Error response = '$feiltekst' fra '${response.request.url}'"
            )
            log.error("HTTP ${response.status.value} ${response.status.description}")

            if (ignorerManglendeTilgangPgaUtdatertTestdata) {
                return null
            } else throw IllegalStateException("Feil ved henting av behandling fra k9-sak")
        }

        return LosObjectMapper.instance.readValue<BehandlingMedFagsakDto>(abc)
    }
}