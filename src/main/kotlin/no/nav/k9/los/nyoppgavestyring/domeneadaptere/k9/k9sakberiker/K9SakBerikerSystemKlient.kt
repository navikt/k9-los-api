package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.k9sakberiker

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.integrasjon.rest.NavHeaders
import no.nav.k9.los.utils.LosObjectMapper
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.BehandlingMedFagsakDto
import no.nav.k9.sak.kontrakt.produksjonsstyring.los.LosOpplysningerSomManglerIKlageDto
import org.slf4j.LoggerFactory
import java.util.*

class K9SakBerikerSystemKlient(
    private val configuration: Configuration,
    private val accessTokenClient: AccessTokenClient,
    scope: String
) : K9SakBerikerInterfaceKludge {
    val log = LoggerFactory.getLogger("K9SakAdapter")
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.k9Url()
    private val scopes = setOf(scope)

    override fun hentBehandling(behandlingUUID: UUID): BehandlingMedFagsakDto? {
        var behandlingDto: BehandlingMedFagsakDto? = null
        runBlocking { behandlingDto = hent(behandlingUUID) }
        return behandlingDto
    }

    override fun berikKlage(påklagdBehandlingUUID: UUID): LosOpplysningerSomManglerIKlageDto? {
        var losOpplysningerSomManglerIKlageDto: LosOpplysningerSomManglerIKlageDto?
        runBlocking { losOpplysningerSomManglerIKlageDto = hentKlagedata(påklagdBehandlingUUID) }
        return losOpplysningerSomManglerIKlageDto
    }

    suspend fun hentKlagedata(påklagdBehandlingUUID: UUID): LosOpplysningerSomManglerIKlageDto? {
        val parameters = listOf(Pair("behandlingUuid", påklagdBehandlingUUID.toString()))
        val httpRequest = "${url}/los/klage/berik"
            .httpGet(parameters)
            .header(
                //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(scopes).asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )

        val (_, response, result) = httpRequest.awaitStringResponseResult()
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

                if (ignorerManglendeTilgangPgaUtdatertTestdata) {
                    return null
                } else throw IllegalStateException("Feil ved henting av behandling fra k9-sak")

            }
        )

        return LosObjectMapper.instance.readValue<LosOpplysningerSomManglerIKlageDto>(abc)
    }

    suspend fun hent(behandlingUUID: UUID): BehandlingMedFagsakDto? {
        val parameters = listOf<Pair<String, String>>(Pair("behandlingUuid", behandlingUUID.toString()))
        val httpRequest = "${url}/los/behandling"
            .httpGet(parameters)
            .header(
                //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(scopes).asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )

        val (_, response, result) = httpRequest.awaitStringResponseResult()
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

                if (ignorerManglendeTilgangPgaUtdatertTestdata) {
                    return null
                } else throw IllegalStateException("Feil ved henting av behandling fra k9-sak")

            }
        )

        return LosObjectMapper.instance.readValue<BehandlingMedFagsakDto>(abc)
    }
}