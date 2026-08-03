package no.nav.k9.los.domeneadaptere.k9.statistikk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Aktør(
    @JsonProperty("aktorId")
    val aktorId: Long,

    @JsonProperty("rolle")
    val rolle: String,

    @JsonProperty("rolleBeskrivelse")
    val rolleBeskrivelse: String
)