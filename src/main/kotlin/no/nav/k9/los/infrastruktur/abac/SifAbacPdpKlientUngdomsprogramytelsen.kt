package no.nav.k9.los.infrastruktur.abac

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.rest.NavHeaders
import no.nav.k9.los.infrastruktur.utils.IkkeImplementertException
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

class SifAbacPdpKlientUngdomsprogramytelsen(
    configuration: Configuration,
    accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient,
) : ISifAbacPdpKlient {
    private val log = LoggerFactory.getLogger(javaClass)
    private val url = configuration.sifAbacPdpUrl()
    private val scopes = setOf(configuration.sifAbacPdpScope())
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)

    override suspend fun hentTilganger(idToken: IIdToken): Tilganger =
        throw IkkeImplementertException("Ikke implementert for ungdomsprogramytelsen")

    override suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode> =
        LosObjectMapper.instance.readValue<List<Diskresjonskode>>(
            kall("diskresjonskoder-sak", "/api/diskresjonskoder/ung/sak", saksnummerDto)
        ).toSet()

    override suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode> =
        LosObjectMapper.instance.readValue<List<Diskresjonskode>>(
            kall("diskresjonskoder-person", "/api/diskresjonskoder/person", aktørId)
        ).toSet()

    override suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto, idToken: IIdToken): Boolean =
        throw IkkeImplementertException("Ikke implementert for ungdomsprogramytelsen")

    override suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>, idToken: IIdToken): Boolean =
        throw IkkeImplementertException("Ikke implementert for ungdomsprogramytelsen")

    private suspend fun kall(
        operation: String,
        endpoint: String,
        body: Any? = null,
        idToken: IIdToken? = null
    ): String {
        val token = if (idToken != null) {
            cachedAccessTokenClient.getOnBehalfOfAccessToken(scopes, idToken.value)
        } else {
            cachedAccessTokenClient.getClientCredentialsAccessToken(scopes)
        }
        val response = Retry.retry(
            tries = 3, operation = operation, initialDelay = Duration.ofMillis(200), factor = 2.0, logger = log,
        ) {
            httpClient.request("$url$endpoint") {
                method = if (body == null) HttpMethod.Get else HttpMethod.Post
                if (body != null) setBody(LosObjectMapper.instance.writeValueAsString(body))
                header(HttpHeaders.Authorization, token.asAuthoriationHeader())
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }
        }
        if (!response.status.isSuccess()) throw SifAbacPdpHttpException(response.status.value, operation)
        return response.bodyAsText()
    }

    override suspend fun harTilgangTilSak(
        action: Action,
        saksnummerDto: SaksnummerDto,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>
    ): Boolean {
        throw IkkeImplementertException("Ikke implementert for ungdomsprogramytelsen")
    }

    override suspend fun harTilgangTilPersoner(
        action: Action,
        aktørIder: List<AktørId>,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>
    ): Boolean {
        throw IkkeImplementertException("Ikke implementert for ungdomsprogramytelsen")
    }
}
