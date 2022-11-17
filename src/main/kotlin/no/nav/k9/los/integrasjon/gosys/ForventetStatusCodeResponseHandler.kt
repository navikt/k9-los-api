package no.nav.k9.los.integrasjon.gosys

import org.apache.http.HttpResponse
import org.apache.http.client.HttpResponseException
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.util.EntityUtils
import java.io.IOException

internal class ForventetStatusCodeResponseHandler(
    private val forventetStatusCode: Int) :
    BasicResponseHandler() {
    @Throws(HttpResponseException::class, IOException::class)
    override fun handleResponse(
        response: HttpResponse
    ): String {
        val statusLine = response.statusLine
        val entity = response.entity
        if (statusLine.statusCode != forventetStatusCode) {
            EntityUtils.consume(entity)
            throw HttpResponseException(
                statusLine.statusCode,
                statusLine.reasonPhrase
            )
        }
        return entity?.let { handleEntity(it) }!!
    }

    companion object {
        fun of(forventetStatusCode: Int): ForventetStatusCodeResponseHandler {
            return ForventetStatusCodeResponseHandler(forventetStatusCode)
        }
    }

}