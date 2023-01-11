package no.nav.k9.los.nyoppgavestyring.query

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonSubTypes(value = [
    JsonSubTypes.Type(value = EnkelSelectFelt::class, name = "enkel"),
    JsonSubTypes.Type(value = AggregertSelectFelt::class, name = "aggregert")
])
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = SelectFelt::class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
open class SelectFelt()