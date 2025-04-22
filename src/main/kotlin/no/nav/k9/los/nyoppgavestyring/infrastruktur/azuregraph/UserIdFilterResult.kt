package no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserId(
    val id: UUID
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserIdFilterResult(
    val value: List<UserId>
)