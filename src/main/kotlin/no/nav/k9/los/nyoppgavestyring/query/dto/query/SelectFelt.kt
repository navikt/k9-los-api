package no.nav.k9.los.nyoppgavestyring.query.dto.query

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SelectFelt(
    val område: String?,
    val kode: String
)