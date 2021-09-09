package no.nav.k9.integrasjon.k9

import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.Configuration
import no.nav.k9.aksjonspunktbehandling.objectMapper
import no.nav.k9.integrasjon.rest.NavHeaders
import no.nav.k9.sak.kontrakt.behandling.BehandlingIdDto
import no.nav.k9.sak.kontrakt.behandling.BehandlingIdListe
import no.nav.k9.utils.Cache
import no.nav.k9.utils.CacheObject
import no.nav.k9.utils.sha512
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

open class K9SakService constructor(
    val configuration: Configuration,
    val accessTokenClient: AccessTokenClient
) : IK9SakService {
    val log = LoggerFactory.getLogger("K9SakService")
    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val cache = Cache<Boolean>(cacheSize = 10000)
    private val url = configuration.k9Url()

    override suspend fun refreshBehandlinger(behandlingIdList: BehandlingIdListe) {
        // Passer på at vi ikke sender behandlingsider om igjen før det har gått 24 timer
        val behandlingIdListe =
            BehandlingIdListe(behandlingIdList.behandlingUuid.filter {
                cache.setIfEmpty(it.toString(), CacheObject(true, expire = LocalDateTime.now().plusHours(12)))
            }.map { BehandlingIdDto(it) }.take(999))

        if (behandlingIdListe.behandlinger.isEmpty()) {
            return
        }
        val body = objectMapper().writeValueAsString(behandlingIdListe)
        if (cache.get(body.sha512()) != null) {
            return
        }
        cache.set(body.sha512(), CacheObject(true, expire = LocalDateTime.now().plusHours(12)))

        val httpRequest = "${url}/behandling/backend-root/refresh"
            .httpPost()
            .body(
                body
            )
            .header(
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(emptySet()).asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )

        val json = Retry.retry(
            operation = "refresh oppgave",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            val (request, _, result) = Operation.monitored(
                app = "k9-los-api",
                operation = "hent-ident",
                resultResolver = { 200 == it.second.statusCode }
            ) { httpRequest.awaitStringResponseResult() }

            result.fold(
                { success ->
                    success
                },
                { error ->
                    log.error(request.toString())
                    log.error(
                        "Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'"
                    )
                    log.error(error.toString())
                }
            )
        }
        log.info("Refreshet " + behandlingIdListe.behandlinger.size +" i k9 sak (" + behandlingIdListe.behandlinger.joinToString(",")+")")
    }
}
