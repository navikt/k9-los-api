package no.nav.k9.integrasjon.gosys

import no.nav.k9.AccessTokenClientResolver
import no.nav.k9.aksjonspunktbehandling.objectMapper
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPatch
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.message.BasicHeader
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.*

class GosysOppgaveGateway constructor(
    val httpClient: CloseableHttpClient,
    val uri: URI,
    private val accessTokenClientResolver: AccessTokenClientResolver
) {

    fun opprettOppgave(request: OpprettGosysOppgaveRequest): GosysOppgave {
        val httpPost = HttpPost(uri)
        val entity = StringEntity(request.body())
        httpPost.entity = entity
        addHeaders(httpPost)

        val responseEntity = httpClient.execute(httpPost, ForventetStatusCodeResponseHandler(201))
        return objectMapper().readValue(responseEntity, GosysOppgave::class.java)
    }

    fun hentOppgaver(request: HentGosysOppgaverRequest): List<GosysOppgave> {
        val httpGet = HttpGet(uri.toString() + request.queryString())
        addHeaders(httpGet)

        val responseEntity = httpClient.execute(httpGet, ForventetStatusCodeResponseHandler(201))
        val json = JSONObject(responseEntity)
        val oppgaver: JSONArray = json.getJSONArray("oppgaver")

        val gosysOppgaver: MutableList<GosysOppgave> = ArrayList()
        oppgaver.forEach { oppgave ->
            val id = (oppgave as JSONObject).getInt("id")
            val versjon = oppgave.getInt("versjon")
            gosysOppgaver.add(
                GosysOppgave(id,versjon )
            )
        }

        return gosysOppgaver
    }

    private fun addHeaders(httpRequestBase: HttpRequestBase) {
        val accessToken = accessTokenClientResolver.naisSts().getAccessToken(emptySet())
        httpRequestBase.setHeaders(mapOf(
            "Authorization" to "${accessToken.tokenType} ${accessToken.accessToken}",
            "X-Correlation-ID" to UUID.randomUUID().toString(),
            "Accept" to "application/json"
        ).map { entry -> BasicHeader(entry.key, entry.value) }.toTypedArray()
        )
    }

    fun avsluttOppgave(request: AvsluttGosysOppgaveRequest) {
        val httpPatch = HttpPatch(uri)
        val entity = StringEntity(request.body())
        httpPatch.entity = entity
        addHeaders(httpPatch)

        httpClient.execute(httpPatch, ForventetStatusCodeResponseHandler(200))
    }
}