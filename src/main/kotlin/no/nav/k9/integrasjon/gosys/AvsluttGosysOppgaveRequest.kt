package no.nav.k9.integrasjon.gosys

import org.json.JSONObject

class AvsluttGosysOppgaveRequest(
    private val versjon: Int,
    private val id: Int
) {


    fun body(): String {
        return JSONObject()
            .put("versjon", versjon)
            .put("id", id)
            .put("status", GosysKonstanter.OppgaveStatus.FERDIGSTILT)
            .toString()
    }
}
