package no.nav.k9.los.nyoppgavestyring.query.dto.query

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderFelt(
    val område: String?,
    val kode: String,
    val økende: Boolean
)