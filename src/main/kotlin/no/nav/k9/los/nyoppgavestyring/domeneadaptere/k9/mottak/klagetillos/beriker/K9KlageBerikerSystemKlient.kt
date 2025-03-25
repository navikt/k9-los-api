package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos.beriker

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.klage.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerHistoriskIKlageDto
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.integrasjon.rest.NavHeaders
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

class K9KlageBerikerSystemKlient(
    private val configuration: Configuration,
    private val accessTokenClient: AccessTokenClient,
    scope: String
) : K9KlageBerikerInterfaceKludge {
    val log = LoggerFactory.getLogger(K9KlageBerikerSystemKlient::class.java)
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.k9KlageUrl()
    private val scopes = setOf(scope)

    override fun hentFraK9Klage(
        påklagdBehandlingUUID: UUID,
        antallForsøk: Int
    ): LosOpplysningerSomManglerHistoriskIKlageDto? {
        return runBlocking { hentKlagedata(påklagdBehandlingUUID, antallForsøk) }
    }

    private suspend fun hentKlagedata(
        påklagdBehandlingUUID: UUID,
        antallForsøk: Int
    ): LosOpplysningerSomManglerHistoriskIKlageDto? {
        val parameters = listOf(Pair("behandlingUuid", påklagdBehandlingUUID.toString()))
        val httpRequest = "${url}/los/historikkutfylling"
            .httpGet(parameters)
            .header(
                //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(scopes).asAuthoriationHeader(),
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
                val feiltekst = error.response.body().asString("text/plain")
                val ignorerManglendeTilgangPgaUtdatertTestdata = configuration.koinProfile == KoinProfile.PREPROD
                        && feiltekst.contains("MANGLER_TILGANG_FEIL")

                log.error(
                    (if (ignorerManglendeTilgangPgaUtdatertTestdata) "IGNORERER I DEV: " else "") +
                            "Error response = '$feiltekst' fra '${httpRequest.url}'"
                )
                log.error(error.toString())
                log.error("${url}/los/historikkutfylling, UUID: $påklagdBehandlingUUID")

                if (ignorerManglendeTilgangPgaUtdatertTestdata) {
                    return null
                } else throw IllegalStateException("Feil ved henting av behandling fra k9-klage")
            }
        )

        return LosObjectMapper.instance.readValue<LosOpplysningerSomManglerHistoriskIKlageDto>(abc)
    }
}