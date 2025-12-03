package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver.restklient

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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

open class K9SakServiceSystemClient(
    val configuration: Configuration,
    val accessTokenClient: AccessTokenClient,
    val scope: String,
    k9SakBehandlingOppfrisketRepository: K9SakBehandlingOppfrisketRepository,
    private val httpClient: HttpClient
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

        Retry.retry(
            operation = "refresh oppgave",
            initialDelay = Duration.ofMillis(200),
            factor = 2.0,
            logger = log
        ) {
            val response = Operation.monitored(
                app = "k9-los-api",
                operation = "hent-ident",
                resultResolver = { 200 == it.status.value }
            ) {
                httpClient.post("${url}/behandling/backend-root/refresh") {
                    setBody(body)
                    header(
                        HttpHeaders.Authorization, cachedAccessTokenClient.getAccessToken(scopes).asAuthoriationHeader()
                    )
                    header(HttpHeaders.Accept, "application/json")
                    header(HttpHeaders.ContentType, "application/json")
                    header(NavHeaders.CallId, UUID.randomUUID().toString())
                }
            }

            if (response.status.isSuccess()) {
                cache.registrerBehandlingerOppfrisket(behandlinger)
                response.bodyAsText()
            } else {
                log.error("Error response = '${begrensLengde(response.bodyAsText(), 1000)}' fra '${response.request.url}'")
                throw RuntimeException("Kunne ikke gjøre refresh-kall til k9sak")
            }
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
