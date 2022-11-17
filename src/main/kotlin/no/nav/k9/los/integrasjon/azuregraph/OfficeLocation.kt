package no.nav.k9.los.integrasjon.azuregraph


import com.fasterxml.jackson.annotation.JsonProperty

data class OfficeLocation(
    @JsonProperty("@odata.context")
    val odataContext: String,
    val officeLocation: String
)

data class OfficeLocationFilterResult(
    @JsonProperty("@odata.context")
    val odataContext: String,
    val value: List<OfficeLocation>

) {
    data class OfficeLocation(
        val officeLocation: String
    )
}
