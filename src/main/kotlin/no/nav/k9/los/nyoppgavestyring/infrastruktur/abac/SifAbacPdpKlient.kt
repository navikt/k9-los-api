package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.NavHeaders
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.sif.abac.kontrakt.abac.BeskyttetRessursActionAttributt
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.ResourceType
import no.nav.sif.abac.kontrakt.abac.dto.OperasjonDto
import no.nav.sif.abac.kontrakt.abac.dto.PersonerOperasjonDto
import no.nav.sif.abac.kontrakt.abac.dto.PersonerOperasjonGrupperDto
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerOperasjonDto
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerOperasjonGrupperDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.coroutines.coroutineContext

class SifAbacPdpKlient(
    configuration: Configuration,
    accessTokenClient: AccessTokenClient,
    scope: String
) : ISifAbacPdpKlient {
    val log = LoggerFactory.getLogger("SifAbacPdpKlient")
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.sifAbacPdpUrl()
    private val scopes = setOf(scope)
    private val environment = configuration.koinProfile

    override suspend fun diskresjonskoderPerson(aktørId: AktørId): Set<Diskresjonskode> {
        val antallForsøk = 3
        val systemToken = cachedAccessTokenClient.getAccessToken(scopes)
        val httpRequest = "${url}/api/diskresjonskoder/person"
            .httpPost()
            .body(LosObjectMapper.instance.writeValueAsString(aktørId))
            .header(
                //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                HttpHeaders.Authorization to systemToken.asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )
        val (_, _, result) = Retry.retry(
            tries = antallForsøk,
            operation = "diskresjonskoder-person",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) { httpRequest.awaitStringResponseResult() }

        val abc = result.fold(
            { success -> success },
            { error -> throw IllegalStateException("Feil ved henting av diskresjonskoder for person fra sif-abac-pdp: $error") }
        )

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
        val httpRequest = completeUrl
            .httpPost()
            .body(body)
            .header(
                //OBS! Dette kalles bare med system token, og skal ikke brukes ved saksbehandler token
                HttpHeaders.Authorization to systemToken.asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )
        val (_, _, result) = Retry.retry(
            tries = antallForsøk,
            operation = "diskresjonskoder-sak",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) { httpRequest.awaitStringResponseResult() }

        val abc = result.fold(
            { success -> success },
            { error -> throw IllegalStateException("Feil ved henting av diskresjonskoder for sak fra sif-abac-pdp $completeUrl for saksnummer $body : $error") }
        )

        return LosObjectMapper.instance.readValue<List<Diskresjonskode>>(abc)
            .toSet()
    }

    override suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto): Boolean {
        val request = SaksnummerOperasjonDto(saksnummerDto, OperasjonDto(ResourceType.FAGSAK, map(action)))
        val antallForsøk = 3
        val jwt = coroutineContext.idToken().value
        val oboToken = cachedAccessTokenClient.getAccessToken(scopes, jwt)
        val httpRequest = "${url}/api/tilgangskontroll/k9/sak"
            .httpPost()
            .body(LosObjectMapper.instance.writeValueAsString(request))
            .header(
                //OBS! Dette kalles bare med obo token
                HttpHeaders.Authorization to oboToken.asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )
        val (_, _, result) = Retry.retry(
            tries = antallForsøk,
            operation = "tilgangskontroll-sak",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) { httpRequest.awaitStringResponseResult() }

        val abc = result.fold(
            { success -> success },
            { error -> throw IllegalStateException("Feil ved sjekk av tilgang til sak mot sif-abac-pdp: $error") }
        )

        return LosObjectMapper.instance.readValue<Decision>(abc) == Decision.Permit
    }

    override suspend fun harTilgangTilSak(action: Action, saksnummerDto: SaksnummerDto, saksbehandlersIdent : String, saksbehandlersGrupper : Set<UUID>): Boolean {
        if (!saksbehandlersIdent.matches(Regex("^[A-ZÆØÅ][0-9]{6}$"))){
            throw IllegalArgumentException("Saksbehandlers ident var '$saksbehandlersIdent', passer ikke med validering")
        }
        if (saksnummerDto.saksnummer.trim().isEmpty()){
            throw IllegalArgumentException("Mangler saksnummer, passer ikke med validering")
        }
        val request = SaksnummerOperasjonGrupperDto(saksbehandlersIdent, saksbehandlersGrupper.toList(), saksnummerDto, OperasjonDto(ResourceType.FAGSAK, map(action)))
        val antallForsøk = 3
        val systemToken = cachedAccessTokenClient.getAccessToken(scopes)
        val body = LosObjectMapper.instance.writeValueAsString(request)
        val httpRequest = "${url}/api/tilgangskontroll/k9/sak-grupper"
            .httpPost()
            .body(body)
            .header(
                HttpHeaders.Authorization to systemToken.asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )
        val (_, _, result) = Retry.retry(
            tries = antallForsøk,
            operation = "tilgangskontroll-sak-grupper",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) { httpRequest.awaitStringResponseResult() }

        val abc = result.fold(
            { success -> success },
            { error -> throw IllegalStateException("Feil ved sjekk av tilgang til sak vha grupper mot sif-abac-pdp ${if (environment == KoinProfile.PREPROD) body else ""}: $error") }
        )

        return LosObjectMapper.instance.readValue<Decision>(abc) == Decision.Permit
    }

    override suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>): Boolean {
        val request = PersonerOperasjonDto(aktørIder, emptyList(), OperasjonDto(ResourceType.FAGSAK, map(action)))
        val antallForsøk = 3
        val jwt = coroutineContext.idToken().value
        val oboToken = cachedAccessTokenClient.getAccessToken(scopes, jwt)
        val httpRequest = "${url}/api/tilgangskontroll/k9/personer"
            .httpPost()
            .body(LosObjectMapper.instance.writeValueAsString(request))
            .header(
                //OBS! Dette kalles bare med obo token
                HttpHeaders.Authorization to oboToken.asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )
        val (_, _, result) = Retry.retry(
            tries = antallForsøk,
            operation = "tilgangskontroll-personer",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) { httpRequest.awaitStringResponseResult() }

        val abc = result.fold(
            { success -> success },
            { error -> throw IllegalStateException("Feil ved sjekk av tilgang til personer mot sif-abac-pdp: $error") }
        )

        return LosObjectMapper.instance.readValue<Decision>(abc) == Decision.Permit
    }


    override suspend fun harTilgangTilPersoner(action: Action, aktørIder: List<AktørId>, saksbehandlersIdent : String, saksbehandlersGrupper : Set<UUID>): Boolean {
        val request = PersonerOperasjonGrupperDto(saksbehandlersIdent,saksbehandlersGrupper.toList(), aktørIder, emptyList(), OperasjonDto(ResourceType.FAGSAK, map(action)))
        val antallForsøk = 3
        val systemToken = cachedAccessTokenClient.getAccessToken(scopes)
        val body = LosObjectMapper.instance.writeValueAsString(request)
        val httpRequest = "${url}/api/tilgangskontroll/k9/personer-grupper"
            .httpPost()
            .body(body)
            .header(
                HttpHeaders.Authorization to systemToken.asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )
        val (_, _, result) = Retry.retry(
            tries = antallForsøk,
            operation = "tilgangskontroll-personer-grupper",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) { httpRequest.awaitStringResponseResult() }

        val abc = result.fold(
            { success -> success },
            { error -> throw IllegalStateException("Feil ved sjekk av tilgang til personer vha grupper mot sif-abac-pdp ${if (environment == KoinProfile.PREPROD) body else ""}: $error") }
        )

        return LosObjectMapper.instance.readValue<Decision>(abc) == Decision.Permit
    }

    private fun map(action: Action): BeskyttetRessursActionAttributt {
        return when (action) {
            Action.read -> BeskyttetRessursActionAttributt.READ
            Action.update -> BeskyttetRessursActionAttributt.UPDATE
            Action.create -> BeskyttetRessursActionAttributt.CREATE
            Action.reserver -> BeskyttetRessursActionAttributt.RESERVER
        }
    }

    enum class Decision {
        Deny,
        Permit
    }
}