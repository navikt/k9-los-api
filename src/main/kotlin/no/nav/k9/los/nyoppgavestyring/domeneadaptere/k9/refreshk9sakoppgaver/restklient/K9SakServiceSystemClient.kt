package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient

import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.*
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.NavHeaders
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.sak.kontrakt.behandling.BehandlingIdDto
import no.nav.k9.sak.kontrakt.behandling.BehandlingIdListe
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

open class K9SakServiceSystemClient constructor(
    val configuration: Configuration,
    val accessTokenClient: AccessTokenClient,
    val scope: String,
    k9SakBehandlingOppfrisketRepository: K9SakBehandlingOppfrisketRepository
) : IK9SakService {
    private val log = LoggerFactory.getLogger("K9SakService")!!

    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)
    private val url = configuration.k9Url()
    private val scopes = setOf(scope)

    private val cache = K9SakBehandlingOppfrisketCache(k9SakBehandlingOppfrisketRepository)

    @WithSpan
    override suspend fun refreshBehandlinger(behandlingUuid: Collection<UUID>) {
        log.info("Forespørsel om refresh av ${behandlingUuid.size} behandlinger")
        val uoppfriskedeBehandlingUuider = cache.filterNotInCache(behandlingUuid)
        log.info("Kommer til å refreshe ${uoppfriskedeBehandlingUuider.size} behandlinger etter sjekk mot cache av oppfriskede behandlinger, i bolker på 100 stykker")
        for (uuids in uoppfriskedeBehandlingUuider.chunked(100)) {
            utførRefreshKallOgOppdaterCache(uuids)
        }
    }

    @WithSpan
    private suspend fun utførRefreshKallOgOppdaterCache(behandlinger: Collection<UUID>) {
        log.info("Trigger refresh av ${behandlinger.size} behandlinger")
        log.info("Behandlinger som refreshes: $behandlinger")

        val dto = BehandlingIdListe(behandlinger.map { BehandlingIdDto(it) })
        val body = LosObjectMapper.instance.writeValueAsString(dto)
        val httpRequest = "${url}/behandling/backend-root/refresh"
            .httpPost()
            .body(
                body
            )
            .header(
                HttpHeaders.Authorization to cachedAccessTokenClient.getAccessToken(scopes).asAuthoriationHeader(),
                HttpHeaders.Accept to "application/json",
                HttpHeaders.ContentType to "application/json",
                NavHeaders.CallId to UUID.randomUUID().toString()
            )

        Retry.retry(
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
                    cache.registrerBehandlingerOppfrisket(behandlinger)
                    success
                },
                { error ->
                    log.error("Error response = '${begrensLengde(error.response.body().asString("text/plain"), 1000)}' fra '${request.url}'", error.exception)
                    throw RuntimeException("Kunne ikke gjøre refresh-kall til k9sak", error.exception)
                }
            )
        }
    }

    private fun begrensLengde(input : String, maxLengde : Int): String {
        return if (input.length > maxLengde) {
            input.substring(0, maxLengde)
        } else {
            input
        }
    }


}
