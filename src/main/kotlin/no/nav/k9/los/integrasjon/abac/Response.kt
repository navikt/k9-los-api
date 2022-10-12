package no.nav.k9.los.integrasjon.abac


import com.fasterxml.jackson.annotation.JsonProperty

data class Response(
    @JsonProperty("Response")
    val response: List<Response>
) {
    data class Response(
        @JsonProperty("Decision")
        val decision: String
    )
}