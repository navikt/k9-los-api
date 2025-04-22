package no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class DirectoryOject (val id: UUID)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DirectoryOjects (val value: List<DirectoryOject>)