package no.nav.k9.los.infrastruktur.abac

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.withTimeoutOrNull
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.infrastruktur.abac.tilganger.InnloggetAnsattUngV2Dto
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.rest.NavHeaders
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.sif.abac.kontrakt.abac.BeskyttetRessursActionAttributt
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.ResourceType
import no.nav.sif.abac.kontrakt.abac.dto.*
import no.nav.sif.abac.kontrakt.abac.resultat.Tilgangsbeslutning
import no.nav.sif.abac.kontrakt.abac.resultat.TilgangsbeslutningOgSporingshint
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class SifAbacPdpKlientAktivitetspenger(
    configuration: Configuration,
    accessTokenClient: AccessTokenClient,
    scope: String,
    private val httpClient: HttpClient,
    private val hentTilgangerTimeout: kotlin.time.Duration = 5.seconds,
) : ISifAbacPdpKlient {
    private val log = LoggerFactory.getLogger(javaClass)
    private val url = configuration.sifAbacPdpUrl()
    private val scopes = setOf(scope)
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val tilgangerCache = Cache<TilgangerCacheKey, Tilganger>(300)

    override suspend fun hentTilganger(idToken: IIdToken): Tilganger =
        tilgangerCache.hentSuspend(TilgangerCacheKey(Områder.AKTIVITETSPENGER, idToken), Duration.ofMinutes(60)) {
            withTimeoutOrNull(hentTilgangerTimeout) {
                LosObjectMapper.instance.readValue<InnloggetAnsattUngV2Dto>(
                    kall("hent-tilganger", "/api/ung/nav-ansatt/v2", idToken = idToken)
                ).tilTilganger()
            } ?: throw SifAbacPdpUtilgjengeligException()
        }

    override suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode> =
        LosObjectMapper.instance.readValue<List<Diskresjonskode>>(
            // Ung-sak er felles PIP for begge ytelser; autorisasjon bruker aktivitetspenger-domenet nedenfor.
            kall("diskresjonskoder-sak", "/api/diskresjonskoder/ung/sak", saksnummerDto)
        ).toSet()

    override suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode> =
        LosObjectMapper.instance.readValue<List<Diskresjonskode>>(
            kall("diskresjonskoder-person", "/api/diskresjonskoder/person", aktørId)
        ).toSet()

    override suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto, idToken: IIdToken): Boolean =
        LosObjectMapper.instance.readValue<TilgangsbeslutningOgSporingshint>(
            kall(
                "tilgangskontroll-sak", "/api/tilgangskontroll/v2/aktivitetspenger/sak-sporingshint",
                SaksnummerOperasjonDto(saksnummerDto, operasjon(action)), idToken,
            )
        ).tilgangsbeslutning().harTilgang()

    override suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>, idToken: IIdToken): Boolean =
        LosObjectMapper.instance.readValue<Tilgangsbeslutning>(
            kall(
                "tilgangskontroll-personer", "/api/tilgangskontroll/v2/aktivitetspenger/personer",
                PersonerOperasjonDto(aktørIder, emptyList(), operasjon(action)), idToken,
            )
        ).harTilgang()

    override suspend fun harTilgangTilSak(
        action: Action, saksnummerDto: SaksnummerDto, saksbehandlersIdent: String, saksbehandlersGrupper: Set<UUID>,
    ): Boolean = throw UnsupportedOperationException("PDP støtter ikke tilgangskontroll for annen saksbehandler i aktivitetspenger")

    override suspend fun harTilgangTilPersoner(
        action: Action, aktørIder: List<AktørId>, saksbehandlersIdent: String, saksbehandlersGrupper: Set<UUID>,
    ): Boolean = throw UnsupportedOperationException("PDP støtter ikke tilgangskontroll for annen saksbehandler i aktivitetspenger")

    private fun operasjon(action: Action) = OperasjonDto(
        ResourceType.FAGSAK,
        when (action) {
            Action.read -> BeskyttetRessursActionAttributt.READ
            Action.create -> BeskyttetRessursActionAttributt.CREATE
            Action.update -> BeskyttetRessursActionAttributt.UPDATE
            // Serveren avviser RESERVER inntil støtte er implementert. Ingen lokal erstatningspolicy.
            Action.reserver -> BeskyttetRessursActionAttributt.RESERVER
        },
        emptySet(),
    )

    private suspend fun kall(operation: String, endpoint: String, body: Any? = null, idToken: IIdToken? = null): String {
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
}
