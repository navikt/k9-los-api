package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac


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