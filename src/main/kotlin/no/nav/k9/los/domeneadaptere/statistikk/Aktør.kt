package no.nav.k9.los.domeneadaptere.statistikk

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.k9.los.infrastruktur.utils.SensitiveField

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Aktør(
    @JsonProperty("aktorId")
    @field:SensitiveField
    val aktorId: Long,

    @JsonProperty("rolle")
    @field:SensitiveField
    val rolle: String,

    @JsonProperty("rolleBeskrivelse")
    @field:SensitiveField
    val rolleBeskrivelse: String
)