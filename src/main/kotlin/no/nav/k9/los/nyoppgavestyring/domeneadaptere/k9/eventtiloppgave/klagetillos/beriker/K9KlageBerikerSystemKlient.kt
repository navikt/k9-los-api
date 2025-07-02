package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
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
    private val accessTokenClient: AccessTokenClient,
    private val scopeSak: String,
    private val scopeKlage: String
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
        val parameters = listOf(Pair("behandlingUuid", påklagdBehandlingUUID.toString()))
        val httpRequest = "${urlSak}/los/klage/berik"
            .httpGet(parameters)
            .header(
                //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(setOf(scopeSak)).asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )

        val (_, response, result) = Retry.retry(
            tries = antallForsøk,
            operation = "berik",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {  httpRequest.awaitStringResponseResult() }

        if (response.statusCode == HttpStatusCode.NoContent.value) {
            return null
        }
        val abc = result.fold(
            { success ->
                success
            },
            { error ->
                if (error.response.statusCode == HttpStatusCode.ServiceUnavailable.value
                    || error.response.statusCode == HttpStatusCode.GatewayTimeout.value
                    || error.response.statusCode == HttpStatusCode.RequestTimeout.value
                ) {
                    throw TransientException("k9sak er ikke tilgjengelig for beriking av k9klage-oppgave, fikk http code ${error.response.statusCode}", error.exception)
                }
                val feiltekst = error.response.body().asString("text/plain")
                val ignorerManglendeTilgangPgaUtdatertTestdata = configuration.koinProfile == KoinProfile.PREPROD
                        && feiltekst.contains("MANGLER_TILGANG_FEIL")

                log.error(
                    (if (ignorerManglendeTilgangPgaUtdatertTestdata) "IGNORERER I DEV: " else "") +
                            "Error response = '$feiltekst' fra '${httpRequest.url}'"
                )
                log.error(error.toString())

                if (ignorerManglendeTilgangPgaUtdatertTestdata) {
                    return null
                } else throw IllegalStateException("Feil ved henting av data fra k9-sak")

            }
        )

        return LosObjectMapper.instance.readValue<LosOpplysningerSomManglerIKlageDto>(abc)
    }

    private suspend fun hentFraK9KlageSuspend(
        påklagdBehandlingUUID: UUID,
        antallForsøk: Int
    ): LosOpplysningerSomManglerHistoriskIKlageDto? {
        val parameters = listOf(Pair("behandlingUuid", påklagdBehandlingUUID.toString()))
        val httpRequest = "${urlKlage}/los/historikkutfylling"
            .httpGet(parameters)
            .header(
                //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(setOf(scopeKlage)).asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )

        val (_, response, result) = Retry.retry(
            tries = antallForsøk,
            operation = "berik",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {  httpRequest.awaitStringResponseResult() }

        if (response.statusCode == HttpStatusCode.NoContent.value) {
            return null
        }

        val abc = result.fold(
            { success ->
                success
            },
            { error ->
                if (error.response.statusCode == HttpStatusCode.ServiceUnavailable.value
                    || error.response.statusCode == HttpStatusCode.GatewayTimeout.value
                    || error.response.statusCode == HttpStatusCode.RequestTimeout.value
                ) {
                    throw TransientException("k9klage er ikke tilgjengelig for beriking av k9klage-oppgave, fikk http code ${error.response.statusCode}", error.exception)
                }
                val feiltekst = error.response.body().asString("text/plain")
                val ignorerManglendeTilgangPgaUtdatertTestdata = configuration.koinProfile == KoinProfile.PREPROD
                        && feiltekst.contains("MANGLER_TILGANG_FEIL")

                log.error(
                    (if (ignorerManglendeTilgangPgaUtdatertTestdata) "IGNORERER I DEV: " else "") +
                            "Error response = '$feiltekst' fra '${httpRequest.url}'"
                )
                log.error(error.toString())
                log.error("${urlKlage}/los/historikkutfylling, UUID: $påklagdBehandlingUUID")

                if (ignorerManglendeTilgangPgaUtdatertTestdata) {
                    return null
                } else throw IllegalStateException("Feil ved henting av behandling fra k9-klage")
            }
        )

        return LosObjectMapper.instance.readValue<LosOpplysningerSomManglerHistoriskIKlageDto>(abc)
    }
}