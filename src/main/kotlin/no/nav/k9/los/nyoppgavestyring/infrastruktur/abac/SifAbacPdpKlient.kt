package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.NavHeaders
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.sif.abac.kontrakt.abac.AksjonspunktType
import no.nav.sif.abac.kontrakt.abac.BeskyttetRessursActionAttributt
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.ResourceType
import no.nav.sif.abac.kontrakt.abac.dto.*
import no.nav.sif.abac.kontrakt.abac.resultat.Tilgangsbeslutning
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.coroutines.coroutineContext

class SifAbacPdpKlient(
    configuration: Configuration,
    accessTokenClient: AccessTokenClient,
    scope: String,
    private val httpClient: HttpClient
) : ISifAbacPdpKlient {
    val log: Logger = LoggerFactory.getLogger("SifAbacPdpKlient")
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.sifAbacPdpUrl()
    private val scopes = setOf(scope)
    private val environment = configuration.koinProfile

    override suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode> {
        val antallForsøk = 3
        val systemToken = cachedAccessTokenClient.getAccessToken(scopes)
        val response = Retry.retry(
            tries = antallForsøk,
            operation = "diskresjonskoder-person",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.post("${url}/api/diskresjonskoder/person") {
                setBody(LosObjectMapper.instance.writeValueAsString(aktørId))
                header(
                    //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                    HttpHeaders.Authorization, systemToken.asAuthoriationHeader()
                )
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }
        }

        val abc = if (response.status.isSuccess()) {
            response.bodyAsText()
        } else {
            throw IllegalStateException("Feil ved henting av diskresjonskoder for person fra sif-abac-pdp: HTTP ${response.status.value} ${response.status.description}")
        }

        return LosObjectMapper.instance.readValue<List<Diskresjonskode>>(abc)
            .toSet()
    }

    override suspend fun diskresjonskoderSak(saksnummerDto: SaksnummerDto): Set<Diskresjonskode> {
        val antallForsøk = 3
        val systemToken = cachedAccessTokenClient.getAccessToken(scopes)

        if (environment == KoinProfile.PREPROD) {
            val tokenUtenSigatur = systemToken.token.substringBeforeLast(".")
            log.info("Kaller med token $tokenUtenSigatur")
        }

        val completeUrl = "${url}/api/diskresjonskoder/k9/sak"
        val body = LosObjectMapper.instance.writeValueAsString(saksnummerDto)
        val response = Retry.retry(
            tries = antallForsøk,
            operation = "diskresjonskoder-sak",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.post(completeUrl) {
                setBody(body)
                header(
                    //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                    HttpHeaders.Authorization, systemToken.asAuthoriationHeader()
                )
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }
        }

        val abc = if (response.status.isSuccess()) {
            response.bodyAsText()
        } else {
            throw IllegalStateException("Feil ved henting av diskresjonskoder for sak fra sif-abac-pdp $completeUrl for saksnummer $body : HTTP ${response.status.value} ${response.status.description}")
        }

        return LosObjectMapper.instance.readValue<List<Diskresjonskode>>(abc)
            .toSet()
    }

    override suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto): Boolean {
        val request = SaksnummerOperasjonDto(saksnummerDto, OperasjonDto(ResourceType.FAGSAK, map(action), emptySet<AksjonspunktType>()))
        val antallForsøk = 3
        val jwt = coroutineContext.idToken().value
        val oboToken = cachedAccessTokenClient.getAccessToken(scopes, jwt)
        val response = Retry.retry(
            tries = antallForsøk,
            operation = "tilgangskontroll-sak",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.post("${url}/api/tilgangskontroll/v2/k9/sak") {
                setBody(LosObjectMapper.instance.writeValueAsString(request))
                header(
                    //OBS! Dette kalles bare med obo token
                    HttpHeaders.Authorization, oboToken.asAuthoriationHeader()
                )
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }
        }

        val abc = if (response.status.isSuccess()) {
            response.bodyAsText()
        } else {
            throw IllegalStateException("Feil ved sjekk av tilgang til sak mot sif-abac-pdp: HTTP ${response.status.value} ${response.status.description}")
        }

        return LosObjectMapper.instance.readValue<Tilgangsbeslutning>(abc).harTilgang()
    }

    override suspend fun harTilgangTilSak(
        action: Action,
        saksnummerDto: SaksnummerDto,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>
    ): Boolean {
        if (!saksbehandlersIdent.matches(Regex("^[A-ZÆØÅ][0-9]{6}$"))) {
            throw IllegalArgumentException("Saksbehandlers ident var '$saksbehandlersIdent', passer ikke med validering")
        }
        if (saksnummerDto.saksnummer.trim().isEmpty()) {
            throw IllegalArgumentException("Mangler saksnummer, passer ikke med validering")
        }
        val request = SaksnummerOperasjonGrupperDto(
            saksbehandlersIdent,
            saksbehandlersGrupper.toList(),
            saksnummerDto,
            OperasjonDto(ResourceType.FAGSAK, map(action), emptySet<AksjonspunktType>())
        )
        val antallForsøk = 3
        val systemToken = cachedAccessTokenClient.getAccessToken(scopes)
        val body = LosObjectMapper.instance.writeValueAsString(request)
        val response = Retry.retry(
            tries = antallForsøk,
            operation = "tilgangskontroll-sak-grupper",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.post("${url}/api/tilgangskontroll/v2/k9/sak-grupper") {
                setBody(body)
                header(
                    HttpHeaders.Authorization, systemToken.asAuthoriationHeader()
                )
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }
        }

        val abc = if (response.status.isSuccess()) {
            response.bodyAsText()
        } else {
            throw IllegalStateException("Feil ved sjekk av tilgang til sak vha grupper mot sif-abac-pdp ${if (environment == KoinProfile.PREPROD) body else ""}: HTTP ${response.status.value} ${response.status.description}")
        }

        return LosObjectMapper.instance.readValue<Tilgangsbeslutning>(abc).harTilgang()
    }

    override suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>): Boolean {
        val request = PersonerOperasjonDto(aktørIder, emptyList(), OperasjonDto(ResourceType.FAGSAK, map(action), emptySet<AksjonspunktType>()))
        val antallForsøk = 3
        val jwt = coroutineContext.idToken().value
        val oboToken = cachedAccessTokenClient.getAccessToken(scopes, jwt)
        val response = Retry.retry(
            tries = antallForsøk,
            operation = "tilgangskontroll-personer",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.post("${url}/api/tilgangskontroll/v2/k9/personer") {
                setBody(LosObjectMapper.instance.writeValueAsString(request))
                header(
                    //OBS! Dette kalles bare med obo token
                    HttpHeaders.Authorization, oboToken.asAuthoriationHeader()
                )
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }
        }

        val abc = if (response.status.isSuccess()) {
            response.bodyAsText()
        } else {
            throw IllegalStateException("Feil ved sjekk av tilgang til personer mot sif-abac-pdp: HTTP ${response.status.value} ${response.status.description}")
        }

        return LosObjectMapper.instance.readValue<Tilgangsbeslutning>(abc).harTilgang()
    }


    override suspend fun harTilgangTilPersoner(
        action: Action,
        aktørIder: List<AktørId>,
        saksbehandlersIdent: String,
        saksbehandlersGrupper: Set<UUID>
    ): Boolean {
        val request = PersonerOperasjonGrupperDto(
            saksbehandlersIdent,
            saksbehandlersGrupper.toList(),
            aktørIder,
            emptyList(),
            OperasjonDto(ResourceType.FAGSAK, map(action), emptySet<AksjonspunktType>())
        )
        val antallForsøk = 3
        val systemToken = cachedAccessTokenClient.getAccessToken(scopes)
        val body = LosObjectMapper.instance.writeValueAsString(request)
        val response = Retry.retry(
            tries = antallForsøk,
            operation = "tilgangskontroll-personer-grupper",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            httpClient.post("${url}/api/tilgangskontroll/v2/k9/personer-grupper") {
                setBody(body)
                header(
                    HttpHeaders.Authorization, systemToken.asAuthoriationHeader()
                )
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                header(NavHeaders.CallId, UUID.randomUUID().toString())
            }
        }

        val abc = if (response.status.isSuccess()) {
            response.bodyAsText()
        } else {
            throw IllegalStateException("Feil ved sjekk av tilgang til personer vha grupper mot sif-abac-pdp ${if (environment == KoinProfile.PREPROD) body else ""}: HTTP ${response.status.value} ${response.status.description}")
        }

        return LosObjectMapper.instance.readValue<Tilgangsbeslutning>(abc).harTilgang()
    }

    private fun map(action: Action): BeskyttetRessursActionAttributt {
        return when (action) {
            Action.read -> BeskyttetRessursActionAttributt.READ
            Action.update -> BeskyttetRessursActionAttributt.UPDATE
            Action.create -> BeskyttetRessursActionAttributt.CREATE
            Action.reserver -> BeskyttetRessursActionAttributt.RESERVER
        }
    }

}