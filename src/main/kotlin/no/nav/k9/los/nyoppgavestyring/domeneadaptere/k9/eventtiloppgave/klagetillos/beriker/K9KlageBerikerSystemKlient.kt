package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker

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
import no.nav.k9.klage.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerHistoriskIKlageDto
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.TransientException
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.NavHeaders
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerIKlageDto
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

class K9KlageBerikerSystemKlient(
    private val configuration: Configuration,
    accessTokenClient: AccessTokenClient,
    private val scopeSak: String,
    private val scopeKlage: String,
    private val httpClient: HttpClient
) : K9KlageBerikerInterfaceKludge {
    val log = LoggerFactory.getLogger(K9KlageBerikerSystemKlient::class.java)
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val urlKlage = configuration.k9KlageUrl()
    private val urlSak = configuration.k9Url()

    override fun hentFraK9Klage(
        påklagdBehandlingUUID: UUID,
        antallForsøk: Int
    ): LosOpplysningerSomManglerHistoriskIKlageDto? {
        return runBlocking { hentFraK9KlageSuspend(påklagdBehandlingUUID, antallForsøk) }
    }

    @WithSpan
    override fun hentFraK9Sak(påklagdBehandlingUUID: UUID, antallForsøk: Int): LosOpplysningerSomManglerIKlageDto? {
        return runBlocking { hentFraK9SakSuspend(påklagdBehandlingUUID, antallForsøk) }
    }

    private suspend fun hentFraK9SakSuspend(påklagdBehandlingUUID: UUID, antallForsøk: Int = 3): LosOpplysningerSomManglerIKlageDto? {
        val response = Retry.retry(
            tries = antallForsøk,
            operation = "berik",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.get("${urlSak}/los/klage/berik") {
                parameter("behandlingUuid", påklagdBehandlingUUID.toString())
                header(
                    //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                    HttpHeaders.Authorization, cachedAccessTokenClient.getAccessToken(setOf(scopeSak)).asAuthoriationHeader()
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
                throw TransientException("k9sak er ikke tilgjengelig for beriking av k9klage-oppgave, fikk http code ${response.status.value}", Exception("HTTP error ${response.status.value}"))
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
            } else throw IllegalStateException("Feil ved henting av data fra k9-sak")
        }

        return LosObjectMapper.instance.readValue<LosOpplysningerSomManglerIKlageDto>(abc)
    }

    private suspend fun hentFraK9KlageSuspend(
        påklagdBehandlingUUID: UUID,
        antallForsøk: Int
    ): LosOpplysningerSomManglerHistoriskIKlageDto? {
        val response = Retry.retry(
            tries = antallForsøk,
            operation = "berik",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.get("${urlKlage}/los/historikkutfylling") {
                parameter("behandlingUuid", påklagdBehandlingUUID.toString())
                header(
                    //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                    HttpHeaders.Authorization, cachedAccessTokenClient.getAccessToken(setOf(scopeKlage)).asAuthoriationHeader()
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
                throw TransientException("k9klage er ikke tilgjengelig for beriking av k9klage-oppgave, fikk http code ${response.status.value}", Exception("HTTP error ${response.status.value}"))
            }
            val feiltekst = response.bodyAsText()
            val ignorerManglendeTilgangPgaUtdatertTestdata = configuration.koinProfile == KoinProfile.PREPROD
                    && feiltekst.contains("MANGLER_TILGANG_FEIL")

            log.error(
                (if (ignorerManglendeTilgangPgaUtdatertTestdata) "IGNORERER I DEV: " else "") +
                        "Error response = '$feiltekst' fra '${response.request.url}'"
            )
            log.error("HTTP ${response.status.value} ${response.status.description}")
            log.error("${urlKlage}/los/historikkutfylling, UUID: $påklagdBehandlingUUID")

            if (ignorerManglendeTilgangPgaUtdatertTestdata) {
                return null
            } else throw IllegalStateException("Feil ved henting av behandling fra k9-klage")
        }

        return LosObjectMapper.instance.readValue<LosOpplysningerSomManglerHistoriskIKlageDto>(abc)
    }
}