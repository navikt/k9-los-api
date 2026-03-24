package no.nav.k9.los.nyoppgavestyring.query.dto.query

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonSubTypes(value = [
    JsonSubTypes.Type(value = EnkelSelectFelt::class, name = "enkel"),
    JsonSubTypes.Type(value = AntallSelectFelt::class, name = "antall"),
    JsonSubTypes.Type(value = GjennomsnittSelectFelt::class, name = "gjennomsnitt"),
    JsonSubTypes.Type(value = MinSelectFelt::class, name = "min"),
    JsonSubTypes.Type(value = MaksSelectFelt::class, name = "maks"),
    JsonSubTypes.Type(value = SumSelectFelt::class, name = "sum")
])
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = SelectFelt::class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
open class SelectFelt()
