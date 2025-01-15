package no.nav.k9.los.nyoppgavestyring.query.dto.query

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonSubTypes(value = [
    Type(value = CombineOppgavefilter::class, name = "combine"),
    Type(value = FeltverdiOppgavefilter::class, name = "feltverdi")
])
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = Oppgavefilter::class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY
)
sealed class Oppgavefilter
